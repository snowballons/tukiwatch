"""
Tests for app/routers/streams.py

Covers:
- GET /api/resolve  — valid URL, invalid URL, cache bypass, auth guard
- POST /api/status-batch — multiple URLs, concurrent processing,
  per-URL error handling, auth guard
"""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from tests.conftest import TEST_API_KEY, VALID_URLS

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

AUTH = {"X-API-Key": TEST_API_KEY}

ONLINE_RESULT = {
    "status": "online",
    "title": "Test Stream",
    "author": "testchannel",
    "thumbnail": "https://example.com/thumb.png",
    "best_quality": "https://example.com/stream.m3u8",
    "all_qualities": {"best": "https://example.com/stream.m3u8"},
    "category": "Gaming",
    "stream_id": "12345",
    "platform": "twitch",
    "stream_types": ["HLS"],
}

OFFLINE_RESULT = {
    "status": "offline",
    "original_url": "https://www.twitch.tv/offlinechannel",
    "platform": "twitch",
}


# ===========================================================================
# /api/resolve
# ===========================================================================


class TestResolveEndpoint:
    """Tests for GET /api/resolve."""

    def test_valid_url_returns_stream_details(self, client):
        """A valid, supported URL must return stream details from the service."""
        with patch(
            "app.routers.streams.stream_service.resolve_stream_details",
            return_value=ONLINE_RESULT,
        ), patch(
            "app.routers.streams.validate_url",
            return_value="https://www.twitch.tv/testchannel",
        ):
            response = client.get(
                "/api/resolve",
                params={"url": "https://www.twitch.tv/testchannel"},
                headers=AUTH,
            )

        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "online"
        assert data["platform"] == "twitch"

    def test_invalid_url_returns_400(self, client):
        """An unsupported or malformed URL must result in a 4xx response."""
        response = client.get(
            "/api/resolve",
            params={"url": "https://unsupported-domain.xyz/stream"},
            headers=AUTH,
        )
        assert response.status_code in (400, 422)

    def test_missing_api_key_returns_401(self, client):
        """Requests without X-API-Key must be rejected before reaching the handler."""
        response = client.get(
            "/api/resolve",
            params={"url": "https://www.twitch.tv/testchannel"},
        )
        assert response.status_code == 401

    def test_wrong_api_key_returns_401(self, client):
        """Requests with an incorrect API key must be rejected."""
        response = client.get(
            "/api/resolve",
            params={"url": "https://www.twitch.tv/testchannel"},
            headers={"X-API-Key": "totally-wrong"},
        )
        assert response.status_code == 401

    def test_cache_bypass_deletes_cache_entry(self, client):
        """When bypass_cache=true the cache entry for the URL must be deleted."""
        with patch("app.routers.streams.cache") as mock_cache, patch(
            "app.routers.streams.stream_service.resolve_stream_details",
            return_value=ONLINE_RESULT,
        ), patch(
            "app.routers.streams.validate_url",
            return_value="https://www.twitch.tv/testchannel",
        ):
            mock_cache.delete = MagicMock()
            response = client.get(
                "/api/resolve",
                params={
                    "url": "https://www.twitch.tv/testchannel",
                    "bypass_cache": "true",
                },
                headers=AUTH,
            )

        assert response.status_code == 200
        mock_cache.delete.assert_called_once_with(
            "resolve:https://www.twitch.tv/testchannel"
        )

    def test_service_exception_returns_error(self, client):
        """Unhandled service exceptions must surface as 500 responses."""
        from fastapi import HTTPException

        with patch(
            "app.routers.streams.stream_service.resolve_stream_details",
            side_effect=Exception("boom"),
        ), patch(
            "app.routers.streams.validate_url",
            return_value="https://www.twitch.tv/testchannel",
        ):
            response = client.get(
                "/api/resolve",
                params={"url": "https://www.twitch.tv/testchannel"},
                headers=AUTH,
            )

        assert response.status_code == 500

    def test_streamlink_api_exception_is_re_raised(self, client):
        """StreamlinkAPIException subclasses must propagate with their HTTP code."""
        from app.exceptions import NoPluginException

        with patch(
            "app.routers.streams.stream_service.resolve_stream_details",
            side_effect=NoPluginException("https://www.twitch.tv/testchannel"),
        ), patch(
            "app.routers.streams.validate_url",
            return_value="https://www.twitch.tv/testchannel",
        ):
            response = client.get(
                "/api/resolve",
                params={"url": "https://www.twitch.tv/testchannel"},
                headers=AUTH,
            )

        assert response.status_code == 400

    def test_missing_url_param_returns_422(self, client):
        """Omitting the required `url` query parameter must return 422."""
        response = client.get("/api/resolve", headers=AUTH)
        assert response.status_code == 422


# ===========================================================================
# /api/status-batch
# ===========================================================================


class TestStatusBatchEndpoint:
    """Tests for POST /api/status-batch."""

    def test_valid_batch_returns_results(self, client):
        """A valid batch of URLs must return a list of StreamStatus objects."""
        from app.models import StreamStatus

        mock_status = StreamStatus(
            url="https://www.twitch.tv/testchannel",
            status="online",
            platform="twitch",
        )

        with patch(
            "app.routers.streams.stream_service.check_single_stream",
            new=AsyncMock(return_value=mock_status),
        ), patch(
            "app.routers.streams.validate_batch_request",
            return_value=["https://www.twitch.tv/testchannel"],
        ):
            response = client.post(
                "/api/status-batch",
                json={"urls": ["https://www.twitch.tv/testchannel"]},
                headers=AUTH,
            )

        assert response.status_code == 200
        data = response.json()
        assert "results" in data
        assert len(data["results"]) == 1
        assert data["results"][0]["status"] == "online"

    def test_multiple_urls_processed_concurrently(self, client):
        """All URLs in the batch must appear in the response."""
        from app.models import StreamStatus

        urls = [
            "https://www.twitch.tv/chan1",
            "https://www.twitch.tv/chan2",
            "https://www.twitch.tv/chan3",
        ]

        def _make_status(url):
            return StreamStatus(url=url, status="online", platform="twitch")

        with patch(
            "app.routers.streams.stream_service.check_single_stream",
            new=AsyncMock(side_effect=lambda u: _make_status(u)),
        ), patch(
            "app.routers.streams.validate_batch_request",
            return_value=urls,
        ):
            response = client.post(
                "/api/status-batch",
                json={"urls": urls},
                headers=AUTH,
            )

        assert response.status_code == 200
        assert len(response.json()["results"]) == 3

    def test_individual_url_exception_becomes_error_status(self, client):
        """If one URL raises an exception it must appear as status=error, not crash."""
        from app.models import StreamStatus

        good_status = StreamStatus(
            url="https://www.twitch.tv/good",
            status="online",
            platform="twitch",
        )

        call_count = 0

        async def _side_effect(url):
            nonlocal call_count
            call_count += 1
            if call_count == 1:
                raise RuntimeError("network failure")
            return good_status

        with patch(
            "app.routers.streams.stream_service.check_single_stream",
            new=_side_effect,
        ), patch(
            "app.routers.streams.validate_batch_request",
            return_value=[
                "https://www.twitch.tv/bad",
                "https://www.twitch.tv/good",
            ],
        ):
            response = client.post(
                "/api/status-batch",
                json={
                    "urls": [
                        "https://www.twitch.tv/bad",
                        "https://www.twitch.tv/good",
                    ]
                },
                headers=AUTH,
            )

        assert response.status_code == 200
        results = response.json()["results"]
        statuses = {r["url"]: r["status"] for r in results}
        assert statuses["https://www.twitch.tv/bad"] == "error"
        assert statuses["https://www.twitch.tv/good"] == "online"

    def test_missing_api_key_returns_401(self, client):
        """Batch requests without X-API-Key must be rejected."""
        response = client.post(
            "/api/status-batch",
            json={"urls": ["https://www.twitch.tv/testchannel"]},
        )
        assert response.status_code == 401

    def test_empty_urls_list_returns_400(self, client):
        """An empty URLs list must be rejected by the validator."""
        response = client.post(
            "/api/status-batch",
            json={"urls": []},
            headers=AUTH,
        )
        assert response.status_code == 400

    def test_too_many_urls_returns_400(self, client):
        """More than 20 URLs must be rejected by the validator."""
        urls = [f"https://www.twitch.tv/chan{i}" for i in range(21)]
        response = client.post(
            "/api/status-batch",
            json={"urls": urls},
            headers=AUTH,
        )
        assert response.status_code == 400

    def test_cache_bypass_deletes_cache_entries(self, client):
        """bypass_cache=true must delete the cache entry for every URL."""
        from app.models import StreamStatus

        urls = [
            "https://www.twitch.tv/chan1",
            "https://www.twitch.tv/chan2",
        ]
        mock_status = StreamStatus(
            url=urls[0], status="online", platform="twitch"
        )

        with patch("app.routers.streams.cache") as mock_cache, patch(
            "app.routers.streams.stream_service.check_single_stream",
            new=AsyncMock(return_value=mock_status),
        ), patch(
            "app.routers.streams.validate_batch_request",
            return_value=urls,
        ):
            mock_cache.delete = MagicMock()
            client.post(
                "/api/status-batch",
                params={"bypass_cache": "true"},
                json={"urls": urls},
                headers=AUTH,
            )

        assert mock_cache.delete.call_count == len(urls)

    def test_missing_body_returns_422(self, client):
        """Sending no JSON body must return 422 (validation error)."""
        response = client.post("/api/status-batch", headers=AUTH)
        assert response.status_code == 422
