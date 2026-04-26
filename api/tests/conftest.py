
"""
Shared pytest fixtures for the streamwatch-api test suite.

Provides a TestClient wired to the FastAPI app, mock Redis/session-pool
objects, and reusable sample data so individual test modules stay concise.
"""

import os
import time
from unittest.mock import MagicMock, patch

import pytest
from fastapi.testclient import TestClient

# ---------------------------------------------------------------------------
# Environment setup — must happen before the app is imported so that
# config.py picks up the test values.
# ---------------------------------------------------------------------------

TEST_API_KEY = "test-api-key-12345"

os.environ.setdefault("API_KEY", TEST_API_KEY)
os.environ.setdefault("REDIS_URL", "")
os.environ.setdefault("REDIS_HOST", "localhost")


# ---------------------------------------------------------------------------
# App fixture
# ---------------------------------------------------------------------------


@pytest.fixture(scope="session")
def app():
    """Return the FastAPI application instance."""
    # Patch session_pool creation so Streamlink sessions are never actually
    # opened during tests.
    with patch("app.session_pool.StreamlinkSessionPool._create_sessions"):
        from main import app as _app

        return _app


@pytest.fixture()
def client(app):
    """Return a synchronous TestClient for the FastAPI app."""
    with TestClient(app, raise_server_exceptions=False) as c:
        yield c


@pytest.fixture()
def auth_headers():
    """Return HTTP headers that satisfy API key authentication."""
    return {"X-API-Key": TEST_API_KEY}


# ---------------------------------------------------------------------------
# Mock Redis client
# ---------------------------------------------------------------------------


@pytest.fixture()
def mock_redis():
    """
    A MagicMock that mimics the redis.Redis interface used by RedisCache.
    Backed by a plain dict so get/set/delete/exists behave realistically.
    """
    store: dict = {}

    redis_mock = MagicMock()

    def _get(key):
        entry = store.get(key)
        if entry is None:
            return None
        value, expires_at = entry
        if expires_at is not None and time.time() > expires_at:
            del store[key]
            return None
        return value

    def _setex(key, ttl, value):
        store[key] = (value, time.time() + ttl)

    def _delete(*keys):
        for k in keys:
            store.pop(k, None)

    def _dbsize():
        return len(store)

    def _flushdb():
        store.clear()

    def _ping():
        return True

    def _info(_section=None):
        return {"used_memory_human": "1.00M"}

    redis_mock.get.side_effect = _get
    redis_mock.setex.side_effect = _setex
    redis_mock.delete.side_effect = _delete
    redis_mock.dbsize.side_effect = _dbsize
    redis_mock.flushdb.side_effect = _flushdb
    redis_mock.ping.side_effect = _ping
    redis_mock.info.side_effect = _info

    return redis_mock


# ---------------------------------------------------------------------------
# Mock Streamlink session
# ---------------------------------------------------------------------------


@pytest.fixture()
def mock_session():
    """
    A MagicMock that mimics a Streamlink session returned by the session pool.
    Callers can customise resolve_url / streams / get_metadata per test.
    """
    session = MagicMock()

    # Default: resolve_url returns a plausible tuple
    session.resolve_url.return_value = (
        "twitch",
        MagicMock(),  # plugin_class
        "https://twitch.tv/testchannel",
    )

    # Default plugin instance behaviour
    plugin_instance = MagicMock()
    plugin_instance.streams.return_value = {
        "best": MagicMock(url="https://example.com/stream.m3u8"),
        "720p": MagicMock(url="https://example.com/stream_720p.m3u8"),
    }
    plugin_instance.get_metadata.return_value = {
        "title": "Test Stream",
        "author": "testchannel",
        "category": "Gaming",
        "id": "12345",
    }

    # Make plugin_class(session, url) return plugin_instance
    session.resolve_url.return_value[1].__call__ = MagicMock(
        return_value=plugin_instance
    )

    return session, plugin_instance


# ---------------------------------------------------------------------------
# Sample test data
# ---------------------------------------------------------------------------

VALID_URLS = [
    "https://www.twitch.tv/testchannel",
    "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    "https://kick.com/testchannel",
]

INVALID_URLS = [
    "",
    "   ",
    "not-a-url",
    "ftp://twitch.tv/channel",
    "https://unsupported-domain.xyz/stream",
]

SAMPLE_STREAM_RESPONSE = {
    "status": "online",
    "title": "Test Stream",
    "author": "testchannel",
    "thumbnail": "https://ui-avatars.com/api/?name=testchannel",
    "best_quality": "https://example.com/stream.m3u8",
    "all_qualities": {
        "best": "https://example.com/stream.m3u8",
        "720p": "https://example.com/stream_720p.m3u8",
    },
    "category": "Gaming",
    "stream_id": "12345",
    "platform": "twitch",
    "stream_types": ["HLS"],
}

SAMPLE_OFFLINE_RESPONSE = {
    "status": "offline",
    "original_url": "https://www.twitch.tv/offlinechannel",
    "platform": "twitch",
}

>>>>>>> main

