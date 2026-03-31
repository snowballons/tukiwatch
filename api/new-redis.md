# Redis Caching Implementation Plan

## Overview

Migrate the current in-memory `SimpleCache` (in `app/cache.py`) to a Redis-backed cache. The migration preserves the existing public interface (`get`, `set`, `delete`, `size`, `clear`) so that all downstream consumers (`app/services/stream_service.py`, `app/routers/streams.py`, `main.py`) require minimal or no changes.

## Current State

**File: `app/cache.py`**
- `SimpleCache` class backed by an in-memory `Dict[str, CacheEntry]`
- `CacheEntry` dataclass holds `data`, `timestamp`, `ttl` and exposes `is_expired()`
- Global singleton: `cache = SimpleCache()`
- Methods: `get(key)`, `set(key, data, ttl)`, `delete(key)`, `clear()`, `size()`

**Consumers:**
| File | Usage |
|---|---|
| `app/services/stream_service.py` | `cache.get()`, `cache.set()` for status (TTL 120s), resolve (TTL 300s), errors (TTL 30s), offline (TTL 60s) |
| `app/routers/streams.py` | `cache.delete()` for bypass-cache logic |
| `main.py` | `cache.size()` in `/cache/stats` endpoint |
| `app/services/stream_service.py` | `_set_cached_flag()` adds `_cached=True` to cached results (both dicts and Pydantic models) |

**Data types stored:**
- `StreamStatus` (Pydantic `BaseModel`) — for status checks
- `dict` — for resolve results and offline markers

## Design Decisions

### 1. Serialization Strategy

Values are either `dict` or Pydantic `StreamStatus` objects. Both must be serialized to JSON strings before storing in Redis.

- **Write path**: Detect type, serialize accordingly. Pydantic models use `model_dump()` before `json.dumps()`. Wrap in a type envelope so the read path knows how to deserialize.
- **Read path**: Parse JSON, check type envelope, reconstruct the original type.

```python
# Envelope format stored in Redis:
{"__type__": "StreamStatus", "data": {...}}  # Pydantic model
{"__type__": "dict", "data": {...}}           # plain dict
```

### 2. Connection Resilience

Redis introduces a network dependency the current in-memory cache does not have. All cache operations must fail gracefully — a Redis outage must not crash the API.

- Wrap `get`, `set`, `delete` in try/except blocks
- On connection error: log a warning, return `None` (for get) or silently skip (for set/delete)
- Use `redis.ConnectionPool` with configurable timeouts and retry on timeout

### 3. Connection Configuration

Support both individual env vars and a single `REDIS_URL` (standard for Railway, Heroku, etc.):

```bash
# Option A: Individual vars
REDIS_HOST=redis.example.com
REDIS_PORT=6379
REDIS_DB=0
REDIS_PASSWORD=secret

# Option B: Connection string (takes precedence if set)
REDIS_URL=redis://:secret@redis.example.com:6379/0
```

### 4. Fallback to In-Memory

If `REDIS_URL` and `REDIS_HOST` are not set, fall back to the existing in-memory `SimpleCache`. This allows local development without a Redis server.

## Dependencies

### `pyproject.toml`

Add `redis` to the `[project] dependencies` array:

```toml
dependencies = [
    "streamlink>=8.2.1",
    "fastapi>=0.135.2",
    "uvicorn>=0.42.0",
    "gunicorn>=25.3.0",
    "python-dotenv>=1.2.2",
    "redis>=5.0.0",
]
```

### `requirements.txt`

Add the same:

```
streamlink>=8.2.1
fastapi>=0.135.2
uvicorn>=0.42.0
gunicorn>=25.3.0
python-dotenv>=1.2.2
redis>=5.0.0
```

Then run:

```bash
uv lock
```

## Environment Variables

### `.env`

```env
# Existing
ALLOWED_ORIGINS=*
TWITCH_OAUTH_TOKEN=

# Redis (optional — omit both to fall back to in-memory cache)
# REDIS_URL=redis://localhost:6379/0
# REDIS_HOST=localhost
# REDIS_PORT=6379
# REDIS_DB=0
# REDIS_PASSWORD=
```

### `.env.railway`

```env
ALLOWED_ORIGINS=*
# TWITCH_OAUTH_TOKEN=your_token_here
# REDIS_URL will be provided by Railway's Redis plugin
```

## Implementation

### Step 1: Update `config.py`

Add Redis configuration fields. These are read from environment variables and used by the cache module to decide whether to use Redis or fall back to in-memory.

```python
import os
from dotenv import load_dotenv

load_dotenv()


class Config:
    ALLOWED_ORIGINS = os.getenv("ALLOWED_ORIGINS", "*").split(",")
    TWITCH_OAUTH_TOKEN = os.getenv("TWITCH_OAUTH_TOKEN", "")

    # Redis configuration
    REDIS_URL = os.getenv("REDIS_URL", "")
    REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
    REDIS_PORT = int(os.getenv("REDIS_PORT", 6379))
    REDIS_DB = int(os.getenv("REDIS_DB", 0))
    REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "") or None


config = Config()
```

### Step 2: Rewrite `app/cache.py`

Replace `SimpleCache` and `CacheEntry` with a `RedisCache` class and a fallback `SimpleCache`. The module exposes a single `cache` singleton that is one or the other depending on configuration.

**Full replacement for `app/cache.py`:**

```python
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

        if config.REDIS_URL:
            self._client = redis.from_url(
                config.REDIS_URL,
                decode_responses=True,
                socket_connect_timeout=5,
                socket_timeout=5,
                retry_on_timeout=True,
            )
            logger.info("Redis cache connected via REDIS_URL")
        else:
            self._client = redis.Redis(
                host=config.REDIS_HOST,
                port=config.REDIS_PORT,
                db=config.REDIS_DB,
                password=config.REDIS_PASSWORD,
                decode_responses=True,
                socket_connect_timeout=5,
                socket_timeout=5,
                retry_on_timeout=True,
            )
            logger.info(
                "Redis cache connected to %s:%s/%s",
                config.REDIS_HOST,
                config.REDIS_PORT,
                config.REDIS_DB,
            )

        # Verify connectivity on startup
        try:
            self._client.ping()
        except redis.ConnectionError as exc:
            logger.error("Redis connection failed: %s", exc)
            raise

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
                "connected_to": (
                    f"{self._client.connection_pool.host}:"
                    f"{self._client.connection_pool.port}/"
                    f"{self._client.connection_pool.db}"
                ),
                "keys": self._client.dbsize(),
                "used_memory_human": info.get("used_memory_human", "unknown"),
            }
        except Exception as exc:
            logger.warning("Redis stats failed: %s", exc)
            return {"type": "RedisCache", "error": str(exc)}


# ---------------------------------------------------------------------------
# In-memory fallback (current SimpleCache, unchanged logic)
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
```

### Step 3: Update `app/services/stream_service.py`

The `_set_cached_flag` helper needs to work with deserialized Pydantic models returned by Redis. The current implementation already handles this correctly — `model_dump` check isn't needed for setting the flag since we set `__dict__["_cached"]` on the Pydantic object directly.

**No changes required** to `stream_service.py`. The `cache.get()` / `cache.set()` interface is preserved, and `_set_cached_flag` works on whatever type is returned.

### Step 4: Update `app/routers/streams.py`

**No changes required.** The routes already use `cache.delete()` after our earlier refactor.

### Step 5: Update `main.py`

The `/cache/stats` endpoint currently calls `cache.size()`. Since both `RedisCache` and `SimpleCache` now have `size()` and `get_stats()`, optionally enrich the endpoint:

```python
@app.get("/cache/stats")
def cache_stats():
    from app.cache import cache
    return {
        "cache": cache.get_stats(),
        "service": "streamlink-api",
    }
```

**This is an optional enhancement.** The existing `cache.size()` call will continue to work unchanged.

### Step 6: Run `uv lock` and verify

```bash
uv lock
uv run ruff check .
uv run python3 -c "
from app.cache import cache
print(f'Cache type: {type(cache).__name__}')
cache.set('test', {'hello': 'world'}, ttl=10)
result = cache.get('test')
print(f'Store/retrieve dict: {result}')
cache.delete('test')
print(f'After delete: {cache.get(\"test\")}')
print(f'Size: {cache.size()}')
print(f'Stats: {cache.get_stats()}')
"
```

## What Does NOT Change

| File | Why no change needed |
|---|---|
| `app/services/stream_service.py` | Uses `cache.get()` / `cache.set()` — interface preserved |
| `app/routers/streams.py` | Uses `cache.delete()` — interface preserved |
| `app/models.py` | Pydantic models unchanged |
| `app/validators.py` | No cache interaction |
| `app/utils.py` | No cache interaction |
| `app/exceptions.py` | No cache interaction |
| `app/middleware.py` | No cache interaction |
| `app/session_pool.py` | No cache interaction |
| `app/rate_limit.py` | No cache interaction |

## Testing Checklist

1. **In-memory fallback** — Remove `REDIS_URL` / `REDIS_HOST` from env, start app, verify `SimpleCache` is used
2. **Redis mode** — Set `REDIS_URL=redis://localhost:6379/0`, start Redis, start app, verify `RedisCache` is used
3. **Status check round-trip** — `GET /resolve?url=https://twitch.tv/shroud` twice, verify second response has `_cached: true`
4. **Bypass cache** — `GET /resolve?url=...&bypass_cache=true`, verify fresh data
5. **Batch status** — `POST /status-batch` with multiple URLs, verify caching per URL
6. **Cache stats** — `GET /cache/stats`, verify `type` field shows correct backend
7. **Redis down** — Stop Redis, verify app continues working (cache misses, no crashes)
8. **Persistence** — Cache data, restart app (not Redis), verify cached data survives
9. **Lint** — `uv run ruff check .` passes

## Rollback

If Redis causes issues in production:

1. Remove `REDIS_URL` / `REDIS_HOST` from environment variables
2. Redeploy — the app falls back to in-memory `SimpleCache` automatically
3. No code changes needed
