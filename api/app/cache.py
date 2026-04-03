import json
import logging
import time
from typing import Any, Optional

from config import config

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Serialization helpers
# ---------------------------------------------------------------------------


def _serialize(value: Any) -> str:
    """Serialize a value for Redis storage with a type envelope."""
    from app.models import StreamStatus

    if isinstance(value, StreamStatus):
        envelope = {"__type__": "StreamStatus", "data": value.model_dump()}
    elif isinstance(value, dict):
        envelope = {"__type__": "dict", "data": value}
    else:
        envelope = {"__type__": "raw", "data": value}
    return json.dumps(envelope)


def _deserialize(raw: str) -> Any:
    """Deserialize a Redis-stored value, reconstructing original types."""
    from app.models import StreamStatus

    try:
        envelope = json.loads(raw)
    except (json.JSONDecodeError, TypeError):
        return raw

    if not isinstance(envelope, dict) or "__type__" not in envelope:
        return envelope

    type_tag = envelope["__type__"]
    data = envelope["data"]

    if type_tag == "StreamStatus":
        return StreamStatus.model_validate(data)
    return data


# ---------------------------------------------------------------------------
# Redis-backed cache
# ---------------------------------------------------------------------------


class RedisCache:
    """Redis-backed cache with graceful error handling."""

    def __init__(self):
        import redis
        from redis.backoff import ExponentialBackoff
        from redis.retry import Retry

        retry = Retry(ExponentialBackoff(), 3)
        conn_kwargs = dict(
            decode_responses=True,
            socket_connect_timeout=5,
            socket_timeout=5,
            health_check_interval=3,
            retry=retry,
        )

        if config.REDIS_URL:
            pool = redis.ConnectionPool.from_url(config.REDIS_URL, **conn_kwargs)
            logger.info("Redis cache connected via REDIS_URL")
        else:
            pool = redis.ConnectionPool(
                host=config.REDIS_HOST,
                port=config.REDIS_PORT,
                db=config.REDIS_DB,
                password=config.REDIS_PASSWORD,
                **conn_kwargs,
            )
            logger.info(
                "Redis cache connected to %s:%s/%s",
                config.REDIS_HOST,
                config.REDIS_PORT,
                config.REDIS_DB,
            )

        self._client = redis.Redis.from_pool(pool)

        # Store display string for get_stats() (ConnectionPool.from_url doesn't expose host/port/db)
        if config.REDIS_URL:
            self._conn_display = config.REDIS_URL.split("@")[-1]
        else:
            self._conn_display = (
                f"{config.REDIS_HOST}:{config.REDIS_PORT}/{config.REDIS_DB}"
            )

        # Verify connectivity on startup
        self._client.ping()

    def get(self, key: str) -> Optional[Any]:
        try:
            raw = self._client.get(key)
            if raw is None:
                return None
            return _deserialize(raw)
        except Exception as exc:
            logger.warning("Redis GET failed for key %s: %s", key, exc)
            return None

    def set(self, key: str, data: Any, ttl: float = 300) -> None:
        try:
            serialized = _serialize(data)
            self._client.setex(key, int(ttl), serialized)
        except Exception as exc:
            logger.warning("Redis SET failed for key %s: %s", key, exc)

    def delete(self, key: str) -> None:
        try:
            self._client.delete(key)
        except Exception as exc:
            logger.warning("Redis DELETE failed for key %s: %s", key, exc)

    def clear(self) -> None:
        try:
            self._client.flushdb()
        except Exception as exc:
            logger.warning("Redis FLUSHDB failed: %s", exc)

    def size(self) -> int:
        try:
            return self._client.dbsize()
        except Exception as exc:
            logger.warning("Redis DBSIZE failed: %s", exc)
            return 0

    def get_stats(self) -> dict:
        try:
            info = self._client.info("memory")
            return {
                "type": "RedisCache",
                "connected_to": self._conn_display,
                "keys": self._client.dbsize(),
                "used_memory_human": info.get("used_memory_human", "unknown"),
            }
        except Exception as exc:
            logger.warning("Redis stats failed: %s", exc)
            return {"type": "RedisCache", "error": str(exc)}


# ---------------------------------------------------------------------------
# In-memory fallback (original SimpleCache logic)
# ---------------------------------------------------------------------------


class CacheEntry:
    __slots__ = ("data", "timestamp", "ttl")

    def __init__(self, data: Any, timestamp: float, ttl: float):
        self.data = data
        self.timestamp = timestamp
        self.ttl = ttl

    def is_expired(self) -> bool:
        return time.time() - self.timestamp > self.ttl


class SimpleCache:
    def __init__(self):
        self._cache: dict[str, CacheEntry] = {}

    def get(self, key: str) -> Optional[Any]:
        if key in self._cache:
            entry = self._cache[key]
            if not entry.is_expired():
                return entry.data
            del self._cache[key]
        return None

    def set(self, key: str, data: Any, ttl: float = 300) -> None:
        self._cache[key] = CacheEntry(data, time.time(), ttl)

    def delete(self, key: str) -> None:
        self._cache.pop(key, None)

    def clear(self) -> None:
        self._cache.clear()

    def size(self) -> int:
        expired = [k for k, v in self._cache.items() if v.is_expired()]
        for k in expired:
            del self._cache[k]
        return len(self._cache)

    def get_stats(self) -> dict:
        return {
            "type": "SimpleCache",
            "keys": self.size(),
        }


# ---------------------------------------------------------------------------
# Singleton: choose Redis or fallback
# ---------------------------------------------------------------------------


def _create_cache():
    """Create the appropriate cache backend based on configuration."""
    if config.REDIS_URL or config.REDIS_HOST != "localhost":
        try:
            return RedisCache()
        except Exception as exc:
            logger.warning(
                "Redis unavailable (%s), falling back to in-memory cache", exc
            )
            return SimpleCache()
    return SimpleCache()


cache = _create_cache()
