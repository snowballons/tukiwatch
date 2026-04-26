"""
Tests for app/validators.py

Covers:
- validate_url(): valid URLs, missing protocol auto-fix, empty/blank input,
  unsupported domains, malicious/edge-case URLs
- validate_batch_request(): valid batch, empty list, over-limit list,
  mixed valid/invalid URLs, all-invalid URLs
"""

import pytest
from fastapi import HTTPException

from app.validators import validate_url, validate_batch_request


# ===========================================================================
# validate_url
# ===========================================================================


class TestValidateUrl:
    """Tests for the validate_url() function."""

    # -----------------------------------------------------------------------
    # Happy-path: supported domains
    # -----------------------------------------------------------------------

    @pytest.mark.parametrize(
        "url",
        [
            "https://www.twitch.tv/testchannel",
            "https://twitch.tv/testchannel",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://kick.com/testchannel",
            "https://www.facebook.com/gaming/live",
            "https://www.instagram.com/p/abc123/",
            "https://www.tiktok.com/@user/live",
            "https://www.bigo.tv/en/pc/live.html",
            "https://www.dailymotion.com/video/abc",
            "https://vimeo.com/123456789",
            "https://steamcommunity.com/broadcast/watch/123",
            "https://www.bilibili.com/video/BV1xx411c7mD",
            "https://www.huya.com/123456",
            "https://picarto.tv/testchannel",
            "https://trovo.live/testchannel",
            "https://vk.com/video-123456_789",
            "https://dlive.tv/testchannel",
            "https://goodgame.ru/channel/testchannel",
            "https://abema.tv/now-on-air/abema-news",
            "https://aloula.sa/live",
        ],
    )
    def test_valid_supported_url_passes(self, url):
        """All URLs from supported streaming platforms must pass validation."""
        result = validate_url(url)
        assert result  # non-empty string returned

    def test_url_without_protocol_gets_https_prepended(self):
        """A URL missing the scheme must have https:// prepended automatically."""
        result = validate_url("twitch.tv/testchannel")
        assert result.startswith("https://")
        assert "twitch.tv" in result

    def test_http_protocol_is_accepted(self):
        """http:// URLs for supported domains must also pass."""
        result = validate_url("http://twitch.tv/testchannel")
        assert "twitch.tv" in result

    def test_www_prefix_is_stripped_for_domain_check(self):
        """www. prefix must not prevent a valid domain from being recognised."""
        result = validate_url("https://www.twitch.tv/testchannel")
        assert "twitch.tv" in result

    def test_returns_stripped_url(self):
        """Leading/trailing whitespace must be removed from the returned URL."""
        result = validate_url("  https://twitch.tv/testchannel  ")
        assert not result.startswith(" ")
        assert not result.endswith(" ")

    # -----------------------------------------------------------------------
    # Error cases
    # -----------------------------------------------------------------------

    def test_empty_string_raises_400(self):
        """An empty string must raise HTTPException with status 400."""
        with pytest.raises(HTTPException) as exc_info:
            validate_url("")
        assert exc_info.value.status_code == 400

    def test_whitespace_only_raises_400(self):
        """A whitespace-only string must raise HTTPException with status 400."""
        with pytest.raises(HTTPException) as exc_info:
            validate_url("   ")
        assert exc_info.value.status_code == 400

    def test_unsupported_domain_raises_422(self):
        """A URL from an unsupported domain must raise HTTPException with status 422."""
        with pytest.raises(HTTPException) as exc_info:
            validate_url("https://unsupported-domain.xyz/stream")
        assert exc_info.value.status_code == 422

    def test_no_netloc_raises_400(self):
        """A URL with no network location must raise HTTPException with status 400."""
        with pytest.raises(HTTPException) as exc_info:
            validate_url("https://")
        assert exc_info.value.status_code == 400

    @pytest.mark.parametrize(
        "malicious_url",
        [
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "file:///etc/passwd",
            "ftp://twitch.tv/channel",
        ],
    )
    def test_malicious_or_unsupported_scheme_rejected(self, malicious_url):
        """Non-http(s) schemes and malicious URLs must be rejected."""
        with pytest.raises(HTTPException):
            validate_url(malicious_url)

    def test_very_long_url_with_valid_domain_passes(self):
        """Extremely long URLs with a valid domain must still pass."""
        long_path = "a" * 2000
        result = validate_url(f"https://twitch.tv/{long_path}")
        assert "twitch.tv" in result

    def test_url_with_special_characters_in_path(self):
        """URLs with query strings and fragments on valid domains must pass."""
        result = validate_url(
            "https://www.youtube.com/watch?v=abc123&t=42s&list=PLxxx"
        )
        assert "youtube.com" in result

    def test_subdomain_of_supported_domain_passes(self):
        """Subdomains of supported domains (e.g. clips.twitch.tv) must pass."""
        result = validate_url("https://clips.twitch.tv/SomeClipName")
        assert result


# ===========================================================================
# validate_batch_request
# ===========================================================================


class TestValidateBatchRequest:
    """Tests for the validate_batch_request() function."""

    def test_valid_batch_returns_list(self):
        """A list of valid URLs must be returned as a validated list."""
        urls = [
            "https://www.twitch.tv/chan1",
            "https://www.twitch.tv/chan2",
        ]
        result = validate_batch_request(urls)
        assert isinstance(result, list)
        assert len(result) == 2

    def test_empty_list_raises_400(self):
        """An empty list must raise HTTPException with status 400."""
        with pytest.raises(HTTPException) as exc_info:
            validate_batch_request([])
        assert exc_info.value.status_code == 400

    def test_too_many_urls_raises_400(self):
        """More than 20 URLs must raise HTTPException with status 400."""
        urls = [f"https://www.twitch.tv/chan{i}" for i in range(21)]
        with pytest.raises(HTTPException) as exc_info:
            validate_batch_request(urls)
        assert exc_info.value.status_code == 400

    def test_exactly_20_urls_passes(self):
        """Exactly 20 URLs must be accepted."""
        urls = [f"https://www.twitch.tv/chan{i}" for i in range(20)]
        result = validate_batch_request(urls)
        assert len(result) == 20

    def test_invalid_urls_are_skipped_not_raised(self):
        """Invalid URLs in a batch must be skipped rather than raising an exception."""
        urls = [
            "https://www.twitch.tv/valid",
            "https://unsupported-domain.xyz/stream",  # invalid — skipped
        ]
        # Should not raise; the invalid URL is skipped
        result = validate_batch_request(urls)
        assert len(result) >= 1

    def test_all_invalid_urls_raises_400(self):
        """If every URL in the batch is invalid the function must raise 400."""
        urls = ["", "   ", "not-a-url-at-all"]
        with pytest.raises(HTTPException) as exc_info:
            validate_batch_request(urls)
        assert exc_info.value.status_code == 400

    def test_blank_urls_are_skipped(self):
        """Blank/empty strings in the batch must be silently skipped."""
        urls = [
            "https://www.twitch.tv/valid",
            "",
            "   ",
        ]
        result = validate_batch_request(urls)
        assert all(u.strip() for u in result)

    def test_single_valid_url_passes(self):
        """A batch with a single valid URL must succeed."""
        result = validate_batch_request(["https://www.twitch.tv/testchannel"])
        assert len(result) == 1

    @pytest.mark.parametrize(
        "urls",
        [
            ["https://www.twitch.tv/a", "https://kick.com/b"],
            ["https://www.youtube.com/watch?v=abc"],
            [f"https://www.twitch.tv/chan{i}" for i in range(5)],
        ],
    )
    def test_various_valid_batches_pass(self, urls):
        """Parametrised check that common valid batches are accepted."""
        result = validate_batch_request(urls)
        assert len(result) > 0
