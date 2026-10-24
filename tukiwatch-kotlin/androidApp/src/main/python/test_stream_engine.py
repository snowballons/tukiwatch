"""Pytest parity suite for stream_engine.py against tukiwatch-core specs."""

import json
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

    def test_kick_splits_on_colon(self):
        res = stream_engine.extract_category_from_json(
            {"metadata": {"title": "Gaming: late night session"}}, "Kick"
        )
        assert res == "Gaming"


class TestViewerCount:
    def test_viewers(self):
        assert stream_engine.extract_viewer_count({"viewers": "1200"}) == 1200

    def test_viewer_count_key(self):
        assert stream_engine.extract_viewer_count({"viewer_count": 42}) == 42

    def test_missing(self):
        assert stream_engine.extract_viewer_count({}) is None

    def test_negative_ignored(self):
        assert stream_engine.extract_viewer_count({"viewers": -1}) is None


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
