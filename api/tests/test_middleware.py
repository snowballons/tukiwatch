"""
Tests for app/middleware.py

Covers:
- CustomRateLimitMiddleware: within-limit, over-limit, rate-limit headers,
  per-endpoint limits, IP extraction helpers
- InMemoryRateLimiter: internal rate limiting logic
- RedisRateLimiter: (tested separately with mocking)
"""

import time
from unittest.mock import MagicMock, patch

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.middleware import CustomRateLimitMiddleware
from app.rate_limiter import InMemoryRateLimiter

# ---------------------------------------------------------------------------
# Helpers — build a minimal app with only the middleware under test
# ---------------------------------------------------------------------------


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
# CustomRateLimitMiddleware tests (integration via TestClient)
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

        with (
            patch.object(RateLimitConfig, "LIMITS", patched_limits),
            TestClient(app, raise_server_exceptions=False) as client,
        ):
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

        with (
            patch.object(RateLimitConfig, "LIMITS", patched_limits),
            TestClient(app, raise_server_exceptions=False) as client,
        ):
            client.get("/api/resolve")
            response = client.get("/api/resolve")

        assert response.status_code == 429
        body = response.json()
        # The middleware returns the 'detail' dict directly as the JSON body
        assert "retry_after" in body or (
            "detail" in body and "retry_after" in body["detail"]
        )

    # -----------------------------------------------------------------------
    # Unit tests for IP extraction helpers
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


# ===========================================================================
# InMemoryRateLimiter tests (unit tests for internal logic)
# ===========================================================================


class TestInMemoryRateLimiter:
    """Tests for the in-memory rate limiter implementation."""

    def test_check_limit_returns_allowed_within_limit(self):
        """check_limit must return allowed=True when under the limit."""
        limiter = InMemoryRateLimiter()
        result = limiter.check_limit("1.2.3.4", "/test", (10, 60))
        assert result.allowed is True
        assert result.retry_after == 0
        assert result.remaining == 9  # 10 - 1
        assert result.limit == 10

    def test_check_limit_returns_limited_when_exceeded(self):
        """check_limit must return allowed=False once the limit is hit."""
        limiter = InMemoryRateLimiter()
        limit = (3, 60)
        ip, endpoint = "1.2.3.4", "/test"

        for _ in range(3):
            result = limiter.check_limit(ip, endpoint, limit)
            assert result.allowed is True

        result = limiter.check_limit(ip, endpoint, limit)
        assert result.allowed is False
        assert result.retry_after > 0
        assert result.remaining == 0

    def test_different_ips_have_independent_limits(self):
        """Rate limits must be tracked per IP independently."""
        limiter = InMemoryRateLimiter()
        limit = (2, 60)

        # IP 1 makes 2 requests
        limiter.check_limit("1.1.1.1", "/test", limit)
        limiter.check_limit("1.1.1.1", "/test", limit)

        # IP 2 should still be allowed
        result = limiter.check_limit("2.2.2.2", "/test", limit)
        assert result.allowed is True

    def test_different_endpoints_have_independent_limits(self):
        """Rate limits must be tracked per endpoint independently."""
        limiter = InMemoryRateLimiter()
        limit = (2, 60)

        # Make 2 requests to endpoint A
        limiter.check_limit("1.2.3.4", "/endpoint_a", limit)
        limiter.check_limit("1.2.3.4", "/endpoint_a", limit)

        # Endpoint B should still be allowed
        result = limiter.check_limit("1.2.3.4", "/endpoint_b", limit)
        assert result.allowed is True

    def test_cleanup_removes_old_entries(self):
        """Expired entries must be purged to prevent memory leaks."""
        limiter = InMemoryRateLimiter()

        # Inject a stale entry (2 hours ago) directly into internal storage
        stale_ts = int((time.time() - 7200) * 1000)
        limiter._requests["1.2.3.4"] = {"/old": [(stale_ts, 1)]}

        # Force cleanup by backdating last_cleanup
        limiter._last_cleanup = time.time() - 400

        limiter._cleanup_old_entries()

        assert "1.2.3.4" not in limiter._requests

    def test_cleanup_preserves_recent_entries(self):
        """Recent entries must survive cleanup."""
        limiter = InMemoryRateLimiter()

        # Add a recent entry
        limiter.check_limit("1.2.3.4", "/test", (10, 60))

        # Force cleanup
        limiter._last_cleanup = time.time() - 400
        limiter._cleanup_old_entries()

        assert "1.2.3.4" in limiter._requests
        assert "/test" in limiter._requests["1.2.3.4"]

    def test_get_stats_returns_correct_info(self):
        """get_stats must return type and key counts."""
        limiter = InMemoryRateLimiter()
        limiter.check_limit("1.2.3.4", "/test", (10, 60))
        limiter.check_limit("5.6.7.8", "/test", (10, 60))

        stats = limiter.get_stats()

        assert stats["type"] == "InMemoryRateLimiter"
        assert stats["tracked_ips"] == 2
        assert stats["total_endpoints"] == 2

    def test_backend_type_property(self):
        """backend_type must return 'memory'."""
        limiter = InMemoryRateLimiter()
        assert limiter.backend_type == "memory"