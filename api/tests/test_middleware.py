"""
Tests for app/middleware.py

Covers:
- APIKeyMiddleware: missing key, invalid key, valid key, non-/api/ bypass
- CustomRateLimitMiddleware: within-limit, over-limit, rate-limit headers,
  per-endpoint limits, IP extraction helpers, cleanup logic
"""

import time
from unittest.mock import MagicMock, patch

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from starlette.requests import Request
from starlette.responses import PlainTextResponse

from app.middleware import APIKeyMiddleware, CustomRateLimitMiddleware

# ---------------------------------------------------------------------------
# Helpers — build a minimal app with only the middleware under test
# ---------------------------------------------------------------------------

TEST_KEY = "secret-key-for-tests"


def _make_app_with_api_key_middleware(api_key: str = TEST_KEY) -> FastAPI:
    """Return a tiny FastAPI app protected by APIKeyMiddleware."""
    mini = FastAPI()
    mini.add_middleware(APIKeyMiddleware)

    @mini.get("/api/protected")
    def protected():
        return {"ok": True}

    @mini.get("/health")
    def health():
        return {"ok": True}

    return mini


def _make_app_with_rate_limit_middleware() -> FastAPI:
    """Return a tiny FastAPI app with only CustomRateLimitMiddleware."""
    mini = FastAPI()
    mini.add_middleware(CustomRateLimitMiddleware)

    @mini.get("/api/resolve")
    def resolve():
        return {"ok": True}

    @mini.get("/health")
    def health():
        return {"ok": True}

    @mini.get("/api/other")
    def other():
        return {"ok": True}

    return mini


# ===========================================================================
# APIKeyMiddleware tests
# ===========================================================================


class TestAPIKeyMiddleware:
    """Tests for the API key authentication middleware."""

    def test_missing_api_key_returns_401(self):
        """Requests to /api/ without X-API-Key header must be rejected."""
        with patch.dict("os.environ", {"API_KEY": TEST_KEY}):
            app = _make_app_with_api_key_middleware()
            with TestClient(app, raise_server_exceptions=False) as client:
                response = client.get("/api/protected")

        assert response.status_code == 401
        assert "Missing API key" in response.json()["detail"]

    def test_invalid_api_key_returns_401(self):
        """Requests to /api/ with a wrong key must be rejected."""
        with patch.dict("os.environ", {"API_KEY": TEST_KEY}):
            app = _make_app_with_api_key_middleware()
            with TestClient(app, raise_server_exceptions=False) as client:
                response = client.get(
                    "/api/protected", headers={"X-API-Key": "wrong-key"}
                )

        assert response.status_code == 401
        assert "Invalid API key" in response.json()["detail"]

    def test_valid_api_key_passes_through(self):
        """Requests to /api/ with the correct key must reach the handler."""
        with patch.dict("os.environ", {"API_KEY": TEST_KEY}):
            app = _make_app_with_api_key_middleware()
            with TestClient(app, raise_server_exceptions=False) as client:
                response = client.get(
                    "/api/protected", headers={"X-API-Key": TEST_KEY}
                )

        assert response.status_code == 200
        assert response.json() == {"ok": True}

    def test_non_api_route_bypasses_auth(self):
        """Routes outside /api/ must not require an API key."""
        with patch.dict("os.environ", {"API_KEY": TEST_KEY}):
            app = _make_app_with_api_key_middleware()
            with TestClient(app, raise_server_exceptions=False) as client:
                response = client.get("/health")

        assert response.status_code == 200

    def test_empty_api_key_env_var_rejects_any_key(self):
        """When API_KEY env var is empty, any provided key is invalid."""
        with patch.dict("os.environ", {"API_KEY": ""}):
            app = _make_app_with_api_key_middleware(api_key="")
            with TestClient(app, raise_server_exceptions=False) as client:
                response = client.get(
                    "/api/protected", headers={"X-API-Key": "anything"}
                )

        # Empty env key means provided key != "" is always wrong
        assert response.status_code == 401

    def test_empty_api_key_env_var_with_empty_header_passes(self):
        """When API_KEY is empty, an empty X-API-Key header is treated as missing."""
        with patch.dict("os.environ", {"API_KEY": ""}):
            app = _make_app_with_api_key_middleware(api_key="")
            with TestClient(app, raise_server_exceptions=False) as client:
                # Sending no header at all → missing key
                response = client.get("/api/protected")

        assert response.status_code == 401
        assert "Missing API key" in response.json()["detail"]


# ===========================================================================
# CustomRateLimitMiddleware tests
# ===========================================================================


class TestCustomRateLimitMiddleware:
    """Tests for the per-endpoint rate limiting middleware."""

    def test_requests_within_limit_are_allowed(self):
        """The first N requests within the window must all succeed."""
        app = _make_app_with_rate_limit_middleware()
        with TestClient(app, raise_server_exceptions=False) as client:
            # /health allows 200 req/min — a handful should all pass
            for _ in range(5):
                response = client.get("/health")
                assert response.status_code == 200

    def test_requests_exceeding_limit_return_429(self):
        """Once the per-endpoint limit is exhausted the middleware returns 429."""
        from app.rate_limit import RateLimitConfig

        app = _make_app_with_rate_limit_middleware()

        # Patch the limit for /api/resolve to 2 req/60 s so we can hit it fast
        patched_limits = dict(RateLimitConfig.LIMITS)
        patched_limits["/api/resolve"] = (2, 60)

        with patch.object(RateLimitConfig, "LIMITS", patched_limits):
            with TestClient(app, raise_server_exceptions=False) as client:
                with patch.dict("os.environ", {"API_KEY": ""}):
                    r1 = client.get("/api/resolve")
                    r2 = client.get("/api/resolve")
                    r3 = client.get("/api/resolve")  # should be rate-limited

        assert r1.status_code == 200
        assert r2.status_code == 200
        assert r3.status_code == 429

    def test_rate_limit_headers_present_on_success(self):
        """Successful responses must carry X-RateLimit-* headers."""
        app = _make_app_with_rate_limit_middleware()
        with TestClient(app, raise_server_exceptions=False) as client:
            response = client.get("/health")

        assert "X-RateLimit-Limit" in response.headers
        assert "X-RateLimit-Remaining" in response.headers
        assert "X-RateLimit-Reset" in response.headers

    def test_rate_limit_remaining_decrements(self):
        """X-RateLimit-Remaining must decrease with each request."""
        app = _make_app_with_rate_limit_middleware()
        with TestClient(app, raise_server_exceptions=False) as client:
            r1 = client.get("/health")
            r2 = client.get("/health")

        remaining1 = int(r1.headers["X-RateLimit-Remaining"])
        remaining2 = int(r2.headers["X-RateLimit-Remaining"])
        assert remaining2 < remaining1

    def test_different_endpoints_have_different_limits(self):
        """The X-RateLimit-Limit header must reflect per-endpoint configuration."""
        from app.rate_limit import RateLimitConfig

        app = _make_app_with_rate_limit_middleware()
        with TestClient(app, raise_server_exceptions=False) as client:
            health_resp = client.get("/health")
            resolve_resp = client.get("/api/resolve")

        health_limit = int(health_resp.headers["X-RateLimit-Limit"])
        resolve_limit = int(resolve_resp.headers["X-RateLimit-Limit"])

        # /health is more permissive than /api/resolve
        assert health_limit > resolve_limit

    def test_429_response_includes_retry_after(self):
        """Rate-limit error responses must include a Retry-After header."""
        from app.rate_limit import RateLimitConfig

        app = _make_app_with_rate_limit_middleware()
        patched_limits = dict(RateLimitConfig.LIMITS)
        patched_limits["/api/resolve"] = (1, 60)

        with patch.object(RateLimitConfig, "LIMITS", patched_limits):
            with TestClient(app, raise_server_exceptions=False) as client:
                client.get("/api/resolve")
                response = client.get("/api/resolve")

        assert response.status_code == 429
        body = response.json()
        # The middleware returns the 'detail' dict directly as the JSON body
        assert "retry_after" in body or ("detail" in body and "retry_after" in body["detail"])

    # -----------------------------------------------------------------------
    # Unit tests for internal helpers
    # -----------------------------------------------------------------------

    def test_get_client_ip_from_x_forwarded_for(self):
        """_get_client_ip must prefer X-Forwarded-For when present."""
        middleware = CustomRateLimitMiddleware(MagicMock())
        request = MagicMock()
        request.headers = {"X-Forwarded-For": "1.2.3.4, 5.6.7.8"}
        request.client = MagicMock(host="10.0.0.1")

        ip = middleware._get_client_ip(request)
        assert ip == "1.2.3.4"

    def test_get_client_ip_from_x_real_ip(self):
        """_get_client_ip must fall back to X-Real-IP when X-Forwarded-For absent."""
        middleware = CustomRateLimitMiddleware(MagicMock())
        request = MagicMock()
        request.headers = {"X-Real-IP": "9.8.7.6"}
        request.client = MagicMock(host="10.0.0.1")

        ip = middleware._get_client_ip(request)
        assert ip == "9.8.7.6"

    def test_get_client_ip_fallback_to_direct(self):
        """_get_client_ip must fall back to request.client.host as last resort."""
        middleware = CustomRateLimitMiddleware(MagicMock())
        request = MagicMock()
        request.headers = {}
        request.client = MagicMock(host="192.168.1.1")

        ip = middleware._get_client_ip(request)
        assert ip == "192.168.1.1"

    def test_is_rate_limited_returns_false_within_limit(self):
        """_is_rate_limited must return (False, 0) when under the limit."""
        middleware = CustomRateLimitMiddleware(MagicMock())
        limited, retry = middleware._is_rate_limited("1.2.3.4", "/test", (10, 60))
        assert limited is False
        assert retry == 0

    def test_is_rate_limited_returns_true_when_exceeded(self):
        """_is_rate_limited must return (True, >0) once the limit is hit."""
        middleware = CustomRateLimitMiddleware(MagicMock())
        limit = (3, 60)
        ip, endpoint = "1.2.3.4", "/test"

        for _ in range(3):
            middleware._is_rate_limited(ip, endpoint, limit)

        limited, retry = middleware._is_rate_limited(ip, endpoint, limit)
        assert limited is True
        assert retry > 0

    def test_cleanup_removes_old_entries(self):
        """_cleanup_old_entries must purge timestamps older than 1 hour."""
        middleware = CustomRateLimitMiddleware(MagicMock())

        # Inject a stale entry (2 hours ago)
        stale_ts = time.time() - 7200
        middleware.requests["1.2.3.4"] = {"/old": [(stale_ts, 1)]}

        # Force cleanup by backdating last_cleanup
        middleware.last_cleanup = time.time() - 400

        middleware._cleanup_old_entries()

        assert "1.2.3.4" not in middleware.requests
