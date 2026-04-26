"""
Tests for app/cache.py

Covers:
- SimpleCache: get/set, TTL expiration, cache miss, delete, clear, size,
  get_stats
- RedisCache: get/set, TTL, cache miss, delete, clear, size, get_stats,
  graceful error handling, serialization round-trips for dict and StreamStatus
- _serialize / _deserialize helpers
- _create_cache() factory: Redis path, fallback to SimpleCache
"""

import json
import time
from unittest.mock import MagicMock, patch

import pytest

from app.models import StreamStatus


# ===========================================================================
# SimpleCache
# ===========================================================================


class TestSimpleCache:
    """Tests for the in-memory SimpleCache implementation."""

    @pytest.fixture()
    def cache(self):
        from app.cache import SimpleCache
        return SimpleCache()

    def test_set_and_get_returns_value(self, cache):
        """A value stored with set() must be retrievable with get()."""
        cache.set("key1", {"data": 42})
        result = cache.get("key1")
        assert result == {"data": 42}

    def test_cache_miss_returns_none(self, cache):
        """get() on a key that was never set must return None."""
        assert cache.get("nonexistent") is None

    def test_expired_entry_returns_none(self, cache):
        """An entry whose TTL has elapsed must not be returned."""
        cache.set("expiring", "value", ttl=0.01)
        time.sleep(0.05)
        assert cache.get("expiring") is None

    def test_non_expired_entry_is_returned(self, cache):
        """An entry within its TTL must still be returned."""
        cache.set("fresh", "value", ttl=60)
        assert cache.get("fresh") == "value"

    def test_delete_removes_existing_key(self, cache):
        """delete() must remove a key so subsequent get() returns None."""
        cache.set("to_delete", "value")
        cache.delete("to_delete")
        assert cache.get("to_delete") is None

    def test_delete_missing_key_does_not_raise(self, cache):
        """delete() on a non-existent key must not raise any exception."""
        cache.delete("ghost_key")  # should be a no-op

    def test_clear_removes_all_entries(self, cache):
        """clear() must remove every cached entry."""
        cache.set("a", 1)
        cache.set("b", 2)
        cache.clear()
        assert cache.get("a") is None
        assert cache.get("b") is None

    def test_size_reflects_live_entries(self, cache):
        """size() must count only non-expired entries."""
        cache.set("x", 1, ttl=60)
        cache.set("y", 2, ttl=60)
        assert cache.size() == 2

    def test_size_excludes_expired_entries(self, cache):
        """size() must not count entries whose TTL has elapsed."""
        cache.set("live", 1, ttl=60)
        cache.set("dead", 2, ttl=0.01)
        time.sleep(0.05)
        assert cache.size() == 1

    def test_get_stats_returns_dict_with_type(self, cache):
        """get_stats() must return a dict containing the cache type."""
        stats = cache.get_stats()
        assert stats["type"] == "SimpleCache"
        assert "keys" in stats

    def test_overwrite_existing_key(self, cache):
        """Setting the same key twice must overwrite the previous value."""
        cache.set("key", "first")
        cache.set("key", "second")
        assert cache.get("key") == "second"

    def test_stores_stream_status_model(self, cache):
        """SimpleCache must store and return Pydantic StreamStatus objects."""
        status = StreamStatus(url="https://twitch.tv/test", status="online")
        cache.set("status:test", status)
        result = cache.get("status:test")
        assert result.status == "online"


# ===========================================================================
# Serialization helpers
# ===========================================================================


class TestSerializationHelpers:
    """Tests for _serialize() and _deserialize() used by RedisCache."""

    def test_serialize_dict_produces_json_string(self):
        """_serialize() on a dict must produce a valid JSON string."""
        from app.cache import _serialize
        raw = _serialize({"key": "value"})
        parsed = json.loads(raw)
        assert parsed["__type__"] == "dict"
        assert parsed["data"] == {"key": "value"}

    def test_serialize_stream_status_encodes_type(self):
        """_serialize() on a StreamStatus must embed the type tag."""
        from app.cache import _serialize
        status = StreamStatus(url="https://twitch.tv/test", status="online")
        raw = _serialize(status)
        parsed = json.loads(raw)
        assert parsed["__type__"] == "StreamStatus"

    def test_deserialize_dict_returns_dict(self):
        """_deserialize() on a dict envelope must return a plain dict."""
        from app.cache import _serialize, _deserialize
        original = {"hello": "world"}
        result = _deserialize(_serialize(original))
        assert result == original

    def test_deserialize_stream_status_returns_model(self):
        """_deserialize() on a StreamStatus envelope must return a StreamStatus."""
        from app.cache import _serialize, _deserialize
        status = StreamStatus(url="https://twitch.tv/test", status="online")
        result = _deserialize(_serialize(status))
        assert isinstance(result, StreamStatus)
        assert result.status == "online"

    def test_deserialize_invalid_json_returns_raw(self):
        """_deserialize() on non-JSON input must return the raw string."""
        from app.cache import _deserialize
        result = _deserialize("not-json-at-all")
        assert result == "not-json-at-all"

    def test_round_trip_preserves_all_fields(self):
        """A full StreamStatus round-trip must preserve every field."""
        from app.cache import _serialize, _deserialize
        status = StreamStatus(
            url="https://twitch.tv/test",
            status="online",
            title="My Stream",
            author="streamer",
            platform="twitch",
            category="Gaming",
            stream_id="abc123",
        )
        result = _deserialize(_serialize(status))
        assert result.title == "My Stream"
        assert result.author == "streamer"
        assert result.category == "Gaming"


# ===========================================================================
# RedisCache (with mocked Redis client)
# ===========================================================================


class TestRedisCache:
    """Tests for the Redis-backed RedisCache using a mock Redis client."""

    @pytest.fixture()
    def redis_cache(self, mock_redis):
        """Return a RedisCache instance wired to the mock Redis client."""
        from app.cache import RedisCache

        with patch("redis.Redis.from_pool", return_value=mock_redis), \
             patch("redis.ConnectionPool.from_url", return_value=MagicMock()), \
             patch("app.cache.config") as mock_config:
            mock_config.REDIS_URL = "redis://localhost:6379/0"
            mock_config.REDIS_HOST = "localhost"
            mock_config.REDIS_PORT = 6379
            mock_config.REDIS_DB = 0
            mock_config.REDIS_PASSWORD = None
            rc = RedisCache.__new__(RedisCache)
            rc._client = mock_redis
            rc._conn_display = "localhost:6379/0"
        return rc

    def test_set_and_get_returns_value(self, redis_cache):
        """Values stored via set() must be retrievable via get()."""
        redis_cache.set("key1", {"data": 42})
        result = redis_cache.get("key1")
        assert result == {"data": 42}

    def test_cache_miss_returns_none(self, redis_cache):
        """get() on an unknown key must return None."""
        assert redis_cache.get("missing_key") is None

    def test_set_stream_status_and_get_returns_model(self, redis_cache):
        """A StreamStatus stored via set() must be returned as a StreamStatus."""
        status = StreamStatus(url="https://twitch.tv/test", status="online")
        redis_cache.set("status:test", status)
        result = redis_cache.get("status:test")
        assert isinstance(result, StreamStatus)
        assert result.status == "online"

    def test_delete_removes_key(self, redis_cache):
        """delete() must remove the key so get() returns None."""
        redis_cache.set("to_delete", "value")
        redis_cache.delete("to_delete")
        assert redis_cache.get("to_delete") is None

    def test_delete_missing_key_does_not_raise(self, redis_cache):
        """delete() on a non-existent key must not raise."""
        redis_cache.delete("ghost")  # no-op

    def test_clear_removes_all_keys(self, redis_cache):
        """clear() must flush all keys from the store."""
        redis_cache.set("a", 1)
        redis_cache.set("b", 2)
        redis_cache.clear()
        assert redis_cache.get("a") is None
        assert redis_cache.get("b") is None

    def test_size_returns_key_count(self, redis_cache):
        """size() must return the number of stored keys."""
        redis_cache.set("x", 1)
        redis_cache.set("y", 2)
        assert redis_cache.size() == 2

    def test_get_stats_returns_expected_keys(self, redis_cache):
        """get_stats() must return a dict with type, connected_to, and keys."""
        stats = redis_cache.get_stats()
        assert stats["type"] == "RedisCache"
        assert "connected_to" in stats
        assert "keys" in stats

    def test_get_handles_redis_exception_gracefully(self, redis_cache):
        """If Redis raises during get(), None must be returned (no crash)."""
        redis_cache._client.get.side_effect = Exception("connection lost")
        result = redis_cache.get("any_key")
        assert result is None

    def test_set_handles_redis_exception_gracefully(self, redis_cache):
        """If Redis raises during set(), the exception must be swallowed."""
        redis_cache._client.setex.side_effect = Exception("connection lost")
        redis_cache.set("key", "value")  # must not raise

    def test_delete_handles_redis_exception_gracefully(self, redis_cache):
        """If Redis raises during delete(), the exception must be swallowed."""
        redis_cache._client.delete.side_effect = Exception("connection lost")
        redis_cache.delete("key")  # must not raise

    def test_ttl_expiry_via_mock(self, redis_cache):
        """Entries stored with a very short TTL must expire in the mock store."""
        redis_cache.set("expiring", "value", ttl=0.01)
        time.sleep(0.05)
        assert redis_cache.get("expiring") is None


# ===========================================================================
# _create_cache factory
# ===========================================================================


class TestCreateCacheFactory:
    """Tests for the _create_cache() module-level factory function."""

    def test_returns_simple_cache_when_no_redis_config(self):
        """Without Redis config, _create_cache() must return a SimpleCache."""
        from app.cache import SimpleCache, _create_cache

        with patch("app.cache.config") as mock_config:
            mock_config.REDIS_URL = ""
            mock_config.REDIS_HOST = "localhost"
            result = _create_cache()

        assert isinstance(result, SimpleCache)

    def test_falls_back_to_simple_cache_on_redis_error(self):
        """If Redis is unreachable, _create_cache() must fall back to SimpleCache."""
        from app.cache import SimpleCache, _create_cache

        with patch("app.cache.config") as mock_config, \
             patch("app.cache.RedisCache", side_effect=Exception("refused")):
            mock_config.REDIS_URL = "redis://localhost:6379"
            mock_config.REDIS_HOST = "localhost"
            result = _create_cache()

        assert isinstance(result, SimpleCache)

    def test_returns_redis_cache_when_redis_url_set(self):
        """When REDIS_URL is configured and Redis is reachable, RedisCache is used."""
        from app.cache import RedisCache, _create_cache

        mock_redis_instance = MagicMock(spec=RedisCache)

        with patch("app.cache.config") as mock_config, \
             patch("app.cache.RedisCache", return_value=mock_redis_instance):
            mock_config.REDIS_URL = "redis://localhost:6379"
            mock_config.REDIS_HOST = "localhost"
            result = _create_cache()

        assert result is mock_redis_instance
