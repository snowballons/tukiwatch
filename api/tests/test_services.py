"""
Tests for app/services/stream_service.py

Covers:
- resolve_stream_details(): online stream, offline stream, cache hit,
  NoPluginError, NoStreamsError, PluginError (browser), generic exception
- check_single_stream(): returns StreamStatus, cache hit, exception handling,
  result caching
- _resolve_stream_sync(): platform detection, session pool interaction
- _set_cached_flag(): dict and Pydantic model variants
"""

import asyncio
from unittest.mock import MagicMock, patch

import pytest
from streamlink.exceptions import NoPluginError, NoStreamsError, PluginError

from app.models import StreamStatus
from app.exceptions import (
    NoPluginException,
    NoStreamsException,
    BrowserRequiredException,
    PluginException,
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

TWITCH_URL = "https://www.twitch.tv/testchannel"
YOUTUBE_URL = "https://www.youtube.com/watch?v=abc123"


def _make_plugin_instance(streams=None, metadata=None):
    """Return a mock plugin instance with configurable streams/metadata."""
    plugin = MagicMock()
    plugin.streams.return_value = streams if streams is not None else {
        "best": MagicMock(url="https://cdn.example.com/stream.m3u8"),
        "720p": MagicMock(url="https://cdn.example.com/720p.m3u8"),
    }
    plugin.get_metadata.return_value = metadata if metadata is not None else {
        "title": "Test Stream",
        "author": "testchannel",
        "category": "Gaming",
        "id": "99999",
    }
    return plugin


def _make_session(plugin_instance, url=TWITCH_URL, plugin_name="twitch"):
    """Return a mock Streamlink session whose resolve_url returns the given plugin."""
    plugin_class = MagicMock(return_value=plugin_instance)
    session = MagicMock()
    session.resolve_url.return_value = (plugin_name, plugin_class, url)
    return session


# ===========================================================================
# resolve_stream_details
# ===========================================================================


class TestResolveStreamDetails:
    """Tests for the synchronous resolve_stream_details() function."""

    def test_online_stream_returns_full_details(self):
        """A live stream must return status=online with all metadata fields."""
        plugin = _make_plugin_instance()
        session = _make_session(plugin)

        with patch("app.services.stream_service.session_pool") as pool, \
             patch("app.services.stream_service.cache") as mock_cache:
            pool.get_session.return_value = session
            mock_cache.get.return_value = None  # cache miss

            from app.services.stream_service import resolve_stream_details
            result = resolve_stream_details(TWITCH_URL)

        assert result["status"] == "online"
        assert result["platform"] == "twitch"
        assert "best_quality" in result
        assert "all_qualities" in result
        assert result["title"] == "Test Stream"
        assert result["author"] == "testchannel"

    def test_offline_stream_returns_offline_status(self):
        """A stream with no available streams must return status=offline."""
        plugin = _make_plugin_instance(streams={})
        session = _make_session(plugin)

        with patch("app.services.stream_service.session_pool") as pool, \
             patch("app.services.stream_service.cache") as mock_cache:
            pool.get_session.return_value = session
            mock_cache.get.return_value = None

            from app.services.stream_service import resolve_stream_details
            result = resolve_stream_details(TWITCH_URL)

        assert result["status"] == "offline"
        assert result["platform"] == "twitch"

    def test_cache_hit_returns_cached_result(self):
        """When the cache contains a result it must be returned without calling Streamlink."""
        cached = {"status": "online", "title": "Cached", "platform": "twitch"}

        with patch("app.services.stream_service.cache") as mock_cache, \
             patch("app.services.stream_service.session_pool") as pool:
            mock_cache.get.return_value = cached

            from app.services.stream_service import resolve_stream_details
            result = resolve_stream_details(TWITCH_URL)

        pool.get_session.assert_not_called()
        assert result["title"] == "Cached"

    def test_cache_hit_sets_cached_flag(self):
        """Cached results must have _cached=True added."""
        cached = {"status": "online", "platform": "twitch"}

        with patch("app.services.stream_service.cache") as mock_cache:
            mock_cache.get.return_value = cached

            from app.services.stream_service import resolve_stream_details
            result = resolve_stream_details(TWITCH_URL)

        assert result.get("_cached") is True

    def test_no_plugin_error_raises_no_plugin_exception(self):
        """NoPluginError from Streamlink must be converted to NoPluginException."""
        session = MagicMock()
        session.resolve_url.side_effect = NoPluginError()

        with patch("app.services.stream_service.session_pool") as pool, \
             patch("app.services.stream_service.cache") as mock_cache:
            pool.get_session.return_value = session
            mock_cache.get.return_value = None

            from app.services.stream_service import resolve_stream_details
            with pytest.raises(NoPluginException):
                resolve_stream_details(TWITCH_URL)

    def test_no_streams_error_raises_no_streams_exception(self):
        """NoStreamsError from Streamlink must be converted to NoStreamsException."""
        session = MagicMock()
        session.resolve_url.side_effect = NoStreamsError()

        with patch("app.services.stream_service.session_pool") as pool, \
             patch("app.services.stream_service.cache") as mock_cache:
            pool.get_session.return_value = session
            mock_cache.get.return_value = None

            from app.services.stream_service import resolve_stream_details
            with pytest.raises(NoStreamsException):
                resolve_stream_details(TWITCH_URL)

    def test_browser_plugin_error_raises_browser_required_exception(self):
        """A PluginError containing browser keywords must raise BrowserRequiredException."""
        session = MagicMock()
        session.resolve_url.side_effect = PluginError(
            "chromium-based web browser is required"
        )

        with patch("app.services.stream_service.session_pool") as pool, \
             patch("app.services.stream_service.cache") as mock_cache:
            pool.get_session.return_value = session
            mock_cache.get.return_value = None

            from app.services.stream_service import resolve_stream_details
            with pytest.raises(BrowserRequiredException):
                resolve_stream_details(TWITCH_URL)

    def test_generic_plugin_error_raises_plugin_exception(self):
        """A non-browser PluginError must be wrapped in PluginException."""
        session = MagicMock()
        session.resolve_url.side_effect = PluginError("some generic plugin error")

        with patch("app.services.stream_service.session_pool") as pool, \
             patch("app.services.stream_service.cache") as mock_cache:
            pool.get_session.return_value = session
            mock_cache.get.return_value = None

            from app.services.stream_service import resolve_stream_details
            with pytest.raises(PluginException):
                resolve_stream_details(TWITCH_URL)

    def test_session_always_returned_to_pool(self):
        """The session must be returned to the pool even when an exception is raised."""
        session = MagicMock()
        session.resolve_url.side_effect = NoPluginError()

        with patch("app.services.stream_service.session_pool") as pool, \
             patch("app.services.stream_service.cache") as mock_cache:
            pool.get_session.return_value = session
            mock_cache.get.return_value = None

            from app.services.stream_service import resolve_stream_details
            with pytest.raises(NoPluginException):
                resolve_stream_details(TWITCH_URL)

        pool.return_session.assert_called_once_with(session)

    def test_successful_result_is_cached(self):
        """A successful resolution must be stored in the cache."""
        plugin = _make_plugin_instance()
        session = _make_session(plugin)

        with patch("app.services.stream_service.session_pool") as pool, \
             patch("app.services.stream_service.cache") as mock_cache:
            pool.get_session.return_value = session
            mock_cache.get.return_value = None

            from app.services.stream_service import resolve_stream_details
            resolve_stream_details(TWITCH_URL)

        mock_cache.set.assert_called_once()
        cache_key = mock_cache.set.call_args[0][0]
        assert cache_key == f"resolve:{TWITCH_URL}"

    def test_twitch_session_options_applied(self):
        """Twitch URLs must trigger Twitch-specific session configuration."""
        plugin = _make_plugin_instance()
        session = _make_session(plugin, url=TWITCH_URL, plugin_name="twitch")

        with patch("app.services.stream_service.session_pool") as pool, \
             patch("app.services.stream_service.cache") as mock_cache:
            pool.get_session.return_value = session
            mock_cache.get.return_value = None

            from app.services.stream_service import resolve_stream_details
            resolve_stream_details(TWITCH_URL)

        # set_option must have been called at least once for Twitch options
        assert session.set_option.called


# ===========================================================================
# check_single_stream
# ===========================================================================


class TestCheckSingleStream:
    """Tests for the async check_single_stream() function."""

    def test_returns_stream_status_object(self):
        """check_single_stream must return a StreamStatus instance."""
        plugin = _make_plugin_instance()
        session = _make_session(plugin)

        with patch("app.services.stream_service.session_pool") as pool, \
             patch("app.services.stream_service.cache") as mock_cache:
            pool.get_session.return_value = session
            mock_cache.get.return_value = None

            from app.services.stream_service import check_single_stream
            result = asyncio.get_event_loop().run_until_complete(
                check_single_stream(TWITCH_URL)
            )

        assert isinstance(result, StreamStatus)
        assert result.status == "online"

    def test_cache_hit_returns_cached_status(self):
        """A cached StreamStatus must be returned without calling Streamlink."""
        cached_status = StreamStatus(
            url=TWITCH_URL, status="online", platform="twitch"
        )

        with patch("app.services.stream_service.cache") as mock_cache, \
             patch("app.services.stream_service.session_pool") as pool:
            mock_cache.get.return_value = cached_status

            from app.services.stream_service import check_single_stream
            result = asyncio.get_event_loop().run_until_complete(
                check_single_stream(TWITCH_URL)
            )

        pool.get_session.assert_not_called()
        assert result.status == "online"

    def test_exception_returns_error_status(self):
        """If _resolve_stream_sync raises, check_single_stream must return status=error."""
        with patch(
            "app.services.stream_service._resolve_stream_sync",
            side_effect=RuntimeError("network failure"),
        ), patch("app.services.stream_service.cache") as mock_cache:
            mock_cache.get.return_value = None

            from app.services.stream_service import check_single_stream
            result = asyncio.get_event_loop().run_until_complete(
                check_single_stream(TWITCH_URL)
            )

        assert result.status == "error"
        assert "network failure" in result.error

    def test_result_is_cached_after_resolution(self):
        """A freshly resolved StreamStatus must be stored in the cache."""
        plugin = _make_plugin_instance()
        session = _make_session(plugin)

        with patch("app.services.stream_service.session_pool") as pool, \
             patch("app.services.stream_service.cache") as mock_cache:
            pool.get_session.return_value = session
            mock_cache.get.return_value = None

            from app.services.stream_service import check_single_stream
            asyncio.get_event_loop().run_until_complete(
                check_single_stream(TWITCH_URL)
            )

        mock_cache.set.assert_called_once()
        cache_key = mock_cache.set.call_args[0][0]
        assert cache_key == f"status:{TWITCH_URL}"

    def test_error_result_is_cached_with_short_ttl(self):
        """Errors must be cached with a shorter TTL (30 s) than successes."""
        with patch(
            "app.services.stream_service._resolve_stream_sync",
            side_effect=RuntimeError("boom"),
        ), patch("app.services.stream_service.cache") as mock_cache:
            mock_cache.get.return_value = None

            from app.services.stream_service import check_single_stream
            asyncio.get_event_loop().run_until_complete(
                check_single_stream(TWITCH_URL)
            )

        _, _kwargs_or_args = (
            mock_cache.set.call_args[0],
            mock_cache.set.call_args,
        )
        # TTL is the third positional arg or the 'ttl' keyword arg
        call_args = mock_cache.set.call_args
        ttl = call_args[1].get("ttl") or call_args[0][2]
        assert ttl == 30


# ===========================================================================
# _set_cached_flag
# ===========================================================================


class TestSetCachedFlag:
    """Tests for the _set_cached_flag() helper."""

    def test_sets_cached_flag_on_dict(self):
        """_set_cached_flag must add _cached=True to a plain dict."""
        from app.services.stream_service import _set_cached_flag

        result = _set_cached_flag({"status": "online"})
        assert result["_cached"] is True

    def test_sets_cached_flag_on_pydantic_model(self):
        """_set_cached_flag must add _cached=True to a Pydantic model instance."""
        from app.services.stream_service import _set_cached_flag

        model = StreamStatus(url=TWITCH_URL, status="online")
        result = _set_cached_flag(model)
        assert result.__dict__.get("_cached") is True
