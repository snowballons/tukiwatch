"""Rate limiter implementations: Redis-backed (distributed) and in-memory (fallback)."""

import logging
import time
from abc import ABC, abstractmethod
from dataclasses import dataclass

from config import config

logger = logging.getLogger(__name__)


@dataclass
class RateLimitResult:
    """Result of a rate limit check."""
    allowed: bool
    retry_after: int  # seconds until next request allowed (0 if allowed)
    remaining: int    # requests remaining in current window
    limit: int        # max requests in window
    reset_time: int   # unix timestamp when window resets


class BaseRateLimiter(ABC):
    """Abstract base class for rate limiters."""

    def check_limit(self, client_key: str, endpoint: str, limit: tuple[int, int]) -> RateLimitResult:
        """Check if request is within rate limit.

        Args:
            client_key: Client tracking key (format: "ip:1.2.3.4" or "token:tw_supp_xxx")
            endpoint: API endpoint path
            limit: Tuple of (max_requests, time_window_seconds)

        Returns:
            RateLimitResult with allowance decision and metadata
        """


    @abstractmethod
    def get_stats(self) -> dict:
        """Get rate limiter statistics."""

    @property
    @abstractmethod
    def backend_type(self) -> str:
        """Return 'redis' or 'memory'."""


# Lua script for atomic sliding window rate limiting in Redis
# KEYS[1] = rate limit key (ratelimit:{ip}:{endpoint})
# ARGV[1] = current timestamp (milliseconds)
# ARGV[2] = window start timestamp (milliseconds) 
# ARGV[3] = max requests
# ARGV[4] = window size (milliseconds)
# ARGV[5] = TTL (seconds)
RATE_LIMIT_SCRIPT = """
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window_start = tonumber(ARGV[2])
local max_requests = tonumber(ARGV[3])
local window_ms = tonumber(ARGV[4])
local ttl = tonumber(ARGV[5])

-- Remove expired entries
redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

-- Count current requests in window
local current_count = redis.call('ZCARD', key)

if current_count >= max_requests then
    -- Rate limited: get oldest entry to calculate retry_after
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local retry_after = 0
    if #oldest > 0 then
        local oldest_time = tonumber(oldest[2])
        retry_after = math.ceil((oldest_time + window_ms - now) / 1000)
        if retry_after < 1 then retry_after = 1 end
    else
        retry_after = math.ceil(window_ms / 1000)
    end
    return {0, retry_after, 0, max_requests, now + window_ms}
end

-- Add current request with unique member
local member = tostring(now) .. ':' .. math.random(1000000)
redis.call('ZADD', key, now, member)

-- Set TTL on key
redis.call('EXPIRE', key, ttl)

local remaining = max_requests - current_count - 1
return {1, 0, remaining, max_requests, now + window_ms}
"""


class RedisRateLimiter(BaseRateLimiter):
    """Redis-backed sliding window rate limiter using sorted sets."""

    def __init__(self):
        import redis
        from redis.backoff import ExponentialBackoff
        from redis.retry import Retry

        retry = Retry(ExponentialBackoff(), 3)
        conn_kwargs = {
            "decode_responses": True,
            "socket_connect_timeout": 5,
            "socket_timeout": 5,
            "health_check_interval": 3,
            "retry": retry,
        }

        if config.REDIS_URL:
            pool = redis.ConnectionPool.from_url(config.REDIS_URL, **conn_kwargs)
            logger.info("Redis rate limiter connected via REDIS_URL")
        else:
            pool = redis.ConnectionPool(
                host=config.REDIS_HOST,
                port=config.REDIS_PORT,
                db=config.REDIS_DB,
                password=config.REDIS_PASSWORD,
                **conn_kwargs,
            )
            logger.info(
                "Redis rate limiter connected to %s:%s/%s",
                config.REDIS_HOST,
                config.REDIS_PORT,
                config.REDIS_DB,
            )

        self._client = redis.Redis.from_pool(pool)
        self._script = self._client.register_script(RATE_LIMIT_SCRIPT)
        self._connected = False
        self._verify_connection()

    def _verify_connection(self) -> None:
        """Verify Redis connectivity on startup."""
        try:
            self._client.ping()
            self._connected = True
            logger.info("Redis rate limiter connection verified")
        except Exception as exc:
            self._connected = False
            logger.warning("Redis rate limiter connection failed: %s", exc)
            raise

    def _make_key(self, client_key: str, endpoint: str) -> str:
        """Create Redis key for rate limiting."""
        # Normalize endpoint to match RateLimitConfig patterns
        normalized = endpoint.lstrip('/')
        if not normalized:
            normalized = 'root'
        # client_key already includes prefix (ip:xxx or token:xxx)
        return f"ratelimit:{client_key}:{normalized}"

    def check_limit(self, client_key: str, endpoint: str, limit: tuple[int, int]) -> RateLimitResult:
        max_requests, time_window = limit
        now_ms = int(time.time() * 1000)
        window_start_ms = now_ms - (time_window * 1000)
        ttl = time_window + 10  # buffer for cleanup

        key = self._make_key(client_key, endpoint)

        try:
            result = self._script(
                keys=[key],
                args=[now_ms, window_start_ms, max_requests, time_window * 1000, ttl]
            )
            allowed, retry_after, remaining, limit_val, reset_time = result
            return RateLimitResult(
                allowed=bool(allowed),
                retry_after=int(retry_after),
                remaining=int(remaining),
                limit=int(limit_val),
                reset_time=int(reset_time / 1000),  # convert to seconds
            )
        except Exception as exc:
            logger.warning("Redis rate limit check failed: %s", exc)
            raise

    def get_stats(self) -> dict:
        try:
            info = self._client.info("memory")
            return {
                "type": "RedisRateLimiter",
                "connected": self._connected,
                "used_memory_human": info.get("used_memory_human", "unknown"),
            }
        except Exception as exc:
            logger.warning("Redis rate limiter stats failed: %s", exc)
            return {"type": "RedisRateLimiter", "connected": False, "error": str(exc)}

    @property
    def backend_type(self) -> str:
        return "redis"


class InMemoryRateLimiter(BaseRateLimiter):
    """In-memory sliding window rate limiter (fallback)."""

    def __init__(self):
        # {client_key: {endpoint: [(timestamp_ms, count), ...]}}
        self._requests: dict[str, dict[str, list]] = {}
        self._cleanup_interval = 300  # 5 minutes
        self._last_cleanup = time.time()

    def _cleanup_old_entries(self) -> None:
        """Remove entries older than 1 hour to prevent memory leaks."""
        current_time = time.time()
        if current_time - self._last_cleanup < self._cleanup_interval:
            return

        cutoff_time = current_time - 3600  # 1 hour ago

        for client_key in list(self._requests.keys()):
            for endpoint in list(self._requests[client_key].keys()):
                self._requests[client_key][endpoint] = [
                    (ts, count)
                    for ts, count in self._requests[client_key][endpoint]
                    if ts > cutoff_time * 1000
                ]
                if not self._requests[client_key][endpoint]:
                    del self._requests[client_key][endpoint]

            if not self._requests[client_key]:
                del self._requests[client_key]

        self._last_cleanup = current_time

    def check_limit(self, client_key: str, endpoint: str, limit: tuple[int, int]) -> RateLimitResult:
        self._cleanup_old_entries()

        max_requests, time_window = limit
        now_ms = int(time.time() * 1000)
        window_start_ms = now_ms - (time_window * 1000)
        reset_time = int(time.time() + time_window)

        # Initialize tracking for this client_key/endpoint if needed
        if client_key not in self._requests:
            self._requests[client_key] = {}
        if endpoint not in self._requests[client_key]:
            self._requests[client_key][endpoint] = []

        # Remove old entries outside the time window
        self._requests[client_key][endpoint] = [
            (ts, count)
            for ts, count in self._requests[client_key][endpoint]
            if ts > window_start_ms
        ]

        # Count current requests in the time window
        current_count = sum(count for _, count in self._requests[client_key][endpoint])

        # Check if limit exceeded
        if current_count >= max_requests:
            # Calculate retry after time
            oldest_request = min(self._requests[client_key][endpoint], key=lambda x: x[0])[0]
            retry_after = int((oldest_request + (time_window * 1000) - now_ms) / 1000) + 1
            retry_after = max(retry_after, 1)
            return RateLimitResult(
                allowed=False,
                retry_after=retry_after,
                remaining=0,
                limit=max_requests,
                reset_time=reset_time,
            )

        # Add current request
        self._requests[client_key][endpoint].append((now_ms, 1))
        remaining = max_requests - current_count - 1

        return RateLimitResult(
            allowed=True,
            retry_after=0,
            remaining=remaining,
            limit=max_requests,
            reset_time=reset_time,
        )
    def get_stats(self) -> dict:
        self._cleanup_old_entries()
        total_keys = sum(len(v) for v in self._requests.values())
        return {
            "type": "InMemoryRateLimiter",
            "tracked_keys": len(self._requests),
            "total_endpoints": total_keys,
        }

    @property
    def backend_type(self) -> str:
        return "memory"




class RateLimiterFactory:
    """Factory for creating the appropriate rate limiter based on configuration."""

    def __init__(self):
        self._limiter: BaseRateLimiter | None = None
        self._backend_type: str = "memory"
        self._redis_connected: bool = False

    def create(self) -> BaseRateLimiter:
        """Create and return the appropriate rate limiter."""
        if self._limiter is not None:
            return self._limiter

        # Check if Redis is configured
        redis_configured = bool(config.REDIS_URL) or config.REDIS_HOST != "localhost"

        if redis_configured:
            try:
                self._limiter = RedisRateLimiter()
                self._backend_type = "redis"
                self._redis_connected = True
                logger.info("Using Redis-backed rate limiter")
                return self._limiter
            except Exception as exc:
                logger.warning(
                    "Redis unavailable (%s), falling back to in-memory rate limiter", exc
                )
                self._redis_connected = False

        # Fallback to in-memory
        self._limiter = InMemoryRateLimiter()
        self._backend_type = "memory"
        self._redis_connected = False
        logger.info("Using in-memory rate limiter")
        return self._limiter

    @property
    def backend_type(self) -> str:
        if self._limiter is None:
            self.create()
        return self._backend_type

    @property
    def redis_connected(self) -> bool:
        if self._limiter is None:
            self.create()
        return self._redis_connected

    def get_stats(self) -> dict:
        if self._limiter is None:
            self.create()
        stats = self._limiter.get_stats()
        stats["backend_type"] = self._backend_type
        stats["redis_connected"] = self._redis_connected
        return stats


# Global factory instance
rate_limiter_factory = RateLimiterFactory()