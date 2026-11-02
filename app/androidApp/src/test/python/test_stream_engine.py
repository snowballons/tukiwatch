"""Pytest parity suite for stream_engine.py against tukiwatch-core specs."""

import json
import subprocess
import sys

from streamlink.exceptions import NoStreamsError

import stream_engine


class TestErrorTaxonomy:
    def test_browser_error_detection_cloudflare(self):
        assert stream_engine.is_browser_error("Cloudflare protection triggered")

    def test_browser_error_detection_chromium(self):
        assert stream_engine.is_browser_error("requires chromium-based web browser")

    def test_browser_error_detection_403(self):
        assert stream_engine.is_browser_error("403 client error: forbidden")

    def test_browser_error_detection_plain_not_browser(self):
        assert not stream_engine.is_browser_error("Plugin failed to load")

    def test_classify_browser_error(self):
        classified = stream_engine.classify_error("https://kick.com/x", "kick", "requires a browser")
        assert classified["type"] == "browser_required"
        assert "Kick" in classified["error"]
        assert classified["alternative"] is not None

    def test_classify_plugin_error(self):
        classified = stream_engine.classify_error("https://twitch.tv/x", "twitch", "stream went offline")
        assert classified["type"] == "plugin_error"
        assert classified["message"] == "stream went offline"
        assert classified["error_class"] == "plugin_error"


class TestNetworkErrorClassification:
    """Transport failures reach us wrapped in PluginError; classify by text."""

    DNS = (
        "Unable to open URL: https://example.invalid/live.m3u8 "
        "(HTTPSConnectionPool(host='example.invalid', port=443): Max retries exceeded "
        "with url: /live.m3u8 (Caused by NameResolutionError(\"Failed to resolve "
        "'example.invalid' ([Errno -2] Name or service not known)\")))"
    )

    def test_dns_failure_is_network(self):
        assert stream_engine.is_network_error(self.DNS)

    def test_dns_failure_keeps_plugin_error_type_but_flags_network(self):
        classified = stream_engine.classify_error(
            "https://example.invalid/live.m3u8", "example", self.DNS
        )
        assert classified["type"] == "plugin_error"
        assert classified["error_class"] == "network"

    def test_connection_refused_is_network(self):
        assert stream_engine.is_network_error("Connection refused")
        assert stream_engine.is_network_error("Read timed out. (read timeout=30)")

    def test_plain_plugin_failure_is_not_network(self):
        assert not stream_engine.is_network_error("stream went offline")
        assert not stream_engine.is_network_error("Unsupported URL")

    def test_unexpected_network_failure_carries_class(self):
        details = stream_engine.unexpected_error_details(self.DNS)
        assert details["type"] == "unexpected"
        assert details["error_class"] == "network"

    def test_unexpected_non_network_failure(self):
        details = stream_engine.unexpected_error_details("RuntimeError: boom")
        assert details["error_class"] == "unexpected"

    def test_check_status_flags_network_failure(self, monkeypatch):
        from streamlink.exceptions import PluginError

        class _NetworkPlugin:
            def __init__(self, session, url):
                pass

            def streams(self):
                raise PluginError(TestNetworkErrorClassification.DNS)

        class _Session:
            def resolve_url(self, url):
                return "fakeplugin", _NetworkPlugin, url

        monkeypatch.setattr(
            stream_engine, "get_session", lambda opts=None: (_Session(), False)
        )
        parsed = json.loads(stream_engine.check_status("https://example.invalid/live"))
        assert parsed["status"] == "error"
        assert parsed["error_details"]["error_class"] == "network"


class TestPlatformExtraction:
    def test_twitch(self):
        assert stream_engine.extract_platform_from_url("https://www.twitch.tv/someone") == "twitch"

    def test_youtube(self):
        assert stream_engine.extract_platform_from_url("https://www.youtube.com/watch?v=x") == "youtube"
        assert stream_engine.extract_platform_from_url("https://youtu.be/abc") == "youtube"

    def test_kick(self):
        assert stream_engine.extract_platform_from_url("https://kick.com/someone") == "kick"

    def test_unknown(self):
        assert stream_engine.extract_platform_from_url("https://example.com/foo") == "example"
        assert stream_engine.extract_platform_from_url("not-a-url") == "unknown"


class TestUrlParsing:
    """Port of tukiwatch-core tests/test_metadata.py::TestUrlParsing."""

    def test_twitch_channel(self):
        meta = stream_engine.parse_url_metadata("https://www.twitch.tv/someuser123")
        assert meta["platform"] == "Twitch"
        assert meta["username"] == "someuser123"
        assert meta["url_type"] == "channel"

    def test_youtube_video(self):
        meta = stream_engine.parse_url_metadata(
            "https://www.youtube.com/watch?v=abcdefghijk"
        )
        assert meta["platform"] == "YouTube"
        assert meta["username"] == "abcdefghijk"
        assert meta["url_type"] == "video"

    def test_youtube_channel_handle(self):
        meta = stream_engine.parse_url_metadata("https://www.youtube.com/@myhandle")
        assert meta["platform"] == "YouTube"
        assert meta["username"] == "myhandle"

    def test_kick_channel(self):
        meta = stream_engine.parse_url_metadata("https://kick.com/streamer")
        assert meta["platform"] == "Kick"
        assert meta["username"] == "streamer"

    def test_tiktok_channel_live(self):
        meta = stream_engine.parse_url_metadata("https://www.tiktok.com/@cukurayanairobibackup/live")
        assert meta["platform"] == "TikTok"
        assert meta["username"] == "cukurayanairobibackup"
        assert meta["url_type"] == "channel"

    def test_tiktok_channel_handle(self):
        meta = stream_engine.parse_url_metadata("https://www.tiktok.com/@creator")
        assert meta["platform"] == "TikTok"
        assert meta["username"] == "creator"
        assert meta["url_type"] == "channel"

    def test_invalid(self):
        meta = stream_engine.parse_url_metadata("garbage")
        assert meta["url_type"] == "parse_error"


class TestCategoryExtraction:
    def test_twitch_uses_game(self):
        res = stream_engine.extract_category_from_json(
            {"metadata": {"game": "Just Chatting", "title": "hello everyone"}}, "Twitch"
        )
        assert res == "Just Chatting"

    def test_twitch_falls_back_to_title(self):
        res = stream_engine.extract_category_from_json(
            {"metadata": {"title": "League of Legends stream"}}, "Twitch"
        )
        assert res == "League"

    def test_youtube_skips_short_first_word(self):
        res = stream_engine.extract_category_from_json(
            {"metadata": {"title": "is this the new meta?"}}, "YouTube"
        )
        assert res == "is this the"

    def test_youtube_short_article_not_in_skip_list(self):
        res = stream_engine.extract_category_from_json(
            {"metadata": {"title": "bg music for study"}}, "YouTube"
        )
        assert res == "music for study"

    def test_youtube_no_data(self):
        assert stream_engine.extract_category_from_json({}, "YouTube") == "N/A"

    def test_kick_splits_on_colon(self):
        res = stream_engine.extract_category_from_json(
            {"metadata": {"title": "Gaming: late night session"}}, "Kick"
        )
        assert res == "Gaming"

    def test_string_form_failure(self):
        assert stream_engine.extract_category_keywords((False, "error"), "Twitch") == "N/A"

    def test_string_form_bad_json(self):
        assert stream_engine.extract_category_keywords((True, "{not json"), "Twitch") == "N/A"


class TestViewerCount:
    def test_viewers(self):
        assert stream_engine.extract_viewer_count({"viewers": "1200"}) == 1200

    def test_viewer_count_key(self):
        assert stream_engine.extract_viewer_count({"viewer_count": 42}) == 42

    def test_online_key(self):
        assert stream_engine.extract_viewer_count({"online": 7}) == 7

    def test_missing(self):
        assert stream_engine.extract_viewer_count({}) is None

    def test_negative_ignored(self):
        assert stream_engine.extract_viewer_count({"viewers": -1}) is None

    def test_non_numeric(self):
        assert stream_engine.extract_viewer_count({"viewers": "abc"}) is None


class TestThumbnail:
    def test_kick_text_color(self):
        url = stream_engine.generate_fallback_thumbnail("kick", "Streamer")
        assert "color=000000" in url

    def test_twitch_purple(self):
        url = stream_engine.generate_fallback_thumbnail("twitch", "Streamer")
        assert "background=9146FF" in url

    def test_default_gray(self):
        url = stream_engine.generate_fallback_thumbnail("unknown", "Streamer")
        assert "background=6B7280" in url


class TestNoPluginErrorFlow:
    def test_resolve_details_no_plugin(self):
        raw = stream_engine.resolve_details("https://example.com/nope", "{}", "all")
        parsed = json.loads(raw)
        assert parsed["status"] == "error"
        assert parsed["error"] == "no_plugin"
        assert parsed["error_details"]["type"] == "no_plugin"
        assert "alternative" in parsed["error_details"]

    def test_check_status_no_plugin(self):
        raw = stream_engine.check_status("https://example.com/nope", "{}")
        parsed = json.loads(raw)
        assert parsed["status"] == "error"
        assert parsed["error"] == "No plugin available for this URL"


# ---------------------------------------------------------------------------
# Deterministic engine-path tests (no network): fake sessions/plugins.
# ---------------------------------------------------------------------------


class _NoStreamsPlugin:
    def __init__(self, session, url):
        pass

    def streams(self):
        # Streamlink's Plugin.streams() normally swallows this into {}; core
        # still maps a surfaced NoStreamsError to offline, and so must we.
        raise NoStreamsError()


class _NoStreamsSession:
    def resolve_url(self, url):
        return "fakeplugin", _NoStreamsPlugin, url


class _BoomPlugin:
    def __init__(self, session, url):
        pass

    def streams(self):
        raise RuntimeError("boom")


class _BoomSession:
    def resolve_url(self, url):
        return "fakeplugin", _BoomPlugin, url


class TestNoStreamsErrorFlow:
    def test_resolve_details_is_offline(self, monkeypatch):
        monkeypatch.setattr(
            stream_engine, "get_session", lambda opts=None: (_NoStreamsSession(), False)
        )
        parsed = json.loads(stream_engine.resolve_details("https://twitch.tv/offline"))
        assert parsed["status"] == "offline"
        assert parsed["error"] is None
        assert parsed["message"] == "No streams available"
        assert parsed["author"] == "fakeplugin"
        assert parsed["streams"] == []
        assert parsed["_timings"]["count"] == 0

    def test_check_status_is_offline(self, monkeypatch):
        monkeypatch.setattr(
            stream_engine, "get_session", lambda opts=None: (_NoStreamsSession(), False)
        )
        parsed = json.loads(stream_engine.check_status("https://twitch.tv/offline"))
        assert parsed["status"] == "offline"
        assert parsed["error"] is None
        assert parsed["title"] == "Offline"
        assert parsed["author"] == "fakeplugin"


class TestUnexpectedErrorFlow:
    def test_resolve_details_carries_error_details(self, monkeypatch):
        monkeypatch.setattr(
            stream_engine, "get_session", lambda opts=None: (_BoomSession(), False)
        )
        parsed = json.loads(stream_engine.resolve_details("https://example.com/x"))
        assert parsed["status"] == "error"
        assert parsed["error"] == "unexpected"
        details = parsed["error_details"]
        assert details["type"] == "unexpected"
        assert details["message"] == "RuntimeError: boom"
        assert details["reason"] == "Unexpected failure during stream resolution"

    def test_check_status_carries_error_details(self, monkeypatch):
        monkeypatch.setattr(
            stream_engine, "get_session", lambda opts=None: (_BoomSession(), False)
        )
        parsed = json.loads(stream_engine.check_status("https://example.com/x"))
        assert parsed["status"] == "error"
        assert parsed["error_details"]["type"] == "unexpected"


class TestResolvePayload:
    def test_metadata_and_best_quality(self, monkeypatch):
        from streamlink.session import Streamlink
        from streamlink.stream.http import HTTPStream

        class _Plugin:
            def __init__(self, session, url):
                self.session = session

            def streams(self):
                stream = HTTPStream(self.session, "https://cdn.example.com/720p.mp4")
                return {"720p": stream, "best": stream}

            def get_metadata(self):
                return {"title": "Title", "author": "Author", "game": "Game", "id": 42}

        class _Session(Streamlink):
            def resolve_url(self, url):
                return "fake", _Plugin, url

        monkeypatch.setattr(
            stream_engine, "get_session", lambda opts=None: (_Session(), False)
        )
        parsed = json.loads(
            stream_engine.resolve_details("https://example.com/live", "{}", "all")
        )
        assert parsed["status"] == "online"
        assert parsed["original_url"] == "https://example.com/live"
        assert parsed["title"] == "Title"
        assert parsed["author"] == "Author"
        assert parsed["category"] == "Game"
        assert parsed["stream_id"] == "42"
        assert parsed["viewer_count"] is None
        assert parsed["stream_types"] == ["HTTP"]
        assert parsed["best_quality"] == "https://cdn.example.com/720p.mp4"
        assert parsed["streams"][0]["quality"] == "720p"
        assert parsed["streams"][0]["playable_direct"] is True


class TestOptionPassThrough:
    def test_check_status_uses_caller_options(self, monkeypatch):
        seen = {}

        def fake_get_session(opts=None):
            seen.update(opts or {})
            return _NoStreamsSession(), False

        monkeypatch.setattr(stream_engine, "get_session", fake_get_session)
        stream_engine.check_status(
            "https://example.com/x", '{"twitch-low-latency": false}'
        )
        assert seen == {"twitch-low-latency": False}

    def test_session_user_agent_complete(self):
        stream_engine._SESSION = None
        stream_engine._SESSION_OPTS_KEY = None
        session, _ = stream_engine.get_session({})
        ua = session.http.headers.get("User-Agent")
        assert ua is not None
        assert "Mozilla/5.0" in ua
        assert "AppleWebKit" in ua
        assert "Chrome" in ua


class TestCli:
    """SubprocessStreamEngine invokes the script as `script <entrypoint> ...`."""

    def test_entrypoint_dispatch(self):
        proc = subprocess.run(
            [sys.executable, stream_engine.__file__, "version"],
            capture_output=True,
            text=True,
            timeout=60,
        )
        assert proc.returncode == 0, proc.stderr
        assert proc.stdout.strip()

    def test_legacy_url_form(self):
        proc = subprocess.run(
            [sys.executable, stream_engine.__file__, "https://example.com/nope", "all"],
            capture_output=True,
            text=True,
            timeout=60,
        )
        assert proc.returncode == 0, proc.stderr
        parsed = json.loads(proc.stdout.strip().splitlines()[-1])
        assert parsed["status"] == "error"
        assert parsed["error"] == "no_plugin"
