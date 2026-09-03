"""
Tests for app/middleware.py

Covers:
- CustomRateLimitMiddleware: within-limit, over-limit, rate-limit headers,
  per-endpoint limits, supporter tier
- InMemoryRateLimiter: internal rate limiting logic
- RedisRateLimiter: (tested separately with mocking)
"""

import time
from unittest.mock import patch

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.middleware import CustomRateLimitMiddleware
from app.rate_limit import RateLimitConfig
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
            # /api/other allows 100 req/min — a handful should all pass
            for _ in range(5):
                response = client.get("/api/other")
                assert response.status_code == 200

    def test_requests_exceeding_limit_return_429(self):
        """Once the per-endpoint limit is exhausted the middleware returns 429."""
        app = _make_app_with_rate_limit_middleware()

        # Patch the limit for /api/resolve to 2 req/60 s so we can hit it fast
        patched_limits = dict(RateLimitConfig.FREE_LIMITS)
        patched_limits["/api/resolve"] = (2, 60)

        with (
            patch.object(RateLimitConfig, "FREE_LIMITS", patched_limits),
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
            response = client.get("/api/other")

        assert "X-RateLimit-Limit" in response.headers
        assert "X-RateLimit-Remaining" in response.headers
        assert "X-RateLimit-Reset" in response.headers

    def test_rate_limit_remaining_decrements(self):
        """X-RateLimit-Remaining must decrease with each request."""
        app = _make_app_with_rate_limit_middleware()
        with TestClient(app, raise_server_exceptions=False) as client:
            r1 = client.get("/api/other")
            r2 = client.get("/api/other")

        remaining1 = int(r1.headers["X-RateLimit-Remaining"])
        remaining2 = int(r2.headers["X-RateLimit-Remaining"])
        assert remaining2 < remaining1

    def test_different_endpoints_have_different_limits(self):
        """The X-RateLimit-Limit header must reflect per-endpoint configuration."""

        app = _make_app_with_rate_limit_middleware()
        with TestClient(app, raise_server_exceptions=False) as client:
            health_resp = client.get("/api/other")
            resolve_resp = client.get("/api/resolve")

        health_limit = int(health_resp.headers["X-RateLimit-Limit"])
        resolve_limit = int(resolve_resp.headers["X-RateLimit-Limit"])

        # /api/other (default 100) is more permissive than /api/resolve (20)
        assert health_limit > resolve_limit

    def test_429_response_includes_retry_after(self):
        """Rate-limit error responses must include a Retry-After header."""
        app = _make_app_with_rate_limit_middleware()
        patched_limits = dict(RateLimitConfig.FREE_LIMITS)
        patched_limits["/api/resolve"] = (1, 60)

        with (
            patch.object(RateLimitConfig, "FREE_LIMITS", patched_limits),
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

    def test_health_endpoint_excluded_from_rate_limiting(self):
        """Health endpoint should be excluded from rate limiting entirely."""
        app = _make_app_with_rate_limit_middleware()
        with TestClient(app, raise_server_exceptions=False) as client:
            # Make many requests to /health - should all succeed
            for _ in range(10):
                resp = client.get("/health")
                assert resp.status_code == 200
            # No rate limit headers on excluded paths
            resp = client.get("/health")
            assert "X-RateLimit-Limit" not in resp.headers


# ===========================================================================
# InMemoryRateLimiter tests (unit tests for internal logic)
# ===========================================================================


class TestInMemoryRateLimiter:
    """Tests for the in-memory rate limiter implementation."""

    def test_check_limit_returns_allowed_within_limit(self):
        """check_limit must return allowed=True when under the limit."""
        limiter = InMemoryRateLimiter()
        result = limiter.check_limit("ip:1.2.3.4", "/test", (10, 60))
        assert result.allowed is True
        assert result.retry_after == 0
        assert result.remaining == 9  # 10 - 1
        assert result.limit == 10

    def test_check_limit_returns_limited_when_exceeded(self):
        """check_limit must return allowed=False once the limit is hit."""
        limiter = InMemoryRateLimiter()
        limit = (3, 60)
        client_key, endpoint = "ip:1.2.3.4", "/test"

        for _ in range(3):
            result = limiter.check_limit(client_key, endpoint, limit)
            assert result.allowed is True

        result = limiter.check_limit(client_key, endpoint, limit)
        assert result.allowed is False
        assert result.retry_after > 0
        assert result.remaining == 0

    def test_different_client_keys_have_independent_limits(self):
        """Rate limits must be tracked per client_key independently."""
        limiter = InMemoryRateLimiter()
        limit = (2, 60)

        # Client 1 makes 2 requests
        limiter.check_limit("ip:1.1.1.1", "/test", limit)
        limiter.check_limit("ip:1.1.1.1", "/test", limit)

        # Client 2 should still be allowed
        result = limiter.check_limit("ip:2.2.2.2", "/test", limit)
        assert result.allowed is True

    def test_different_endpoints_have_independent_limits(self):
        """Rate limits must be tracked per endpoint independently."""
        limiter = InMemoryRateLimiter()
        limit = (2, 60)

        # Make 2 requests to endpoint A
        limiter.check_limit("ip:1.2.3.4", "/endpoint_a", limit)
        limiter.check_limit("ip:1.2.3.4", "/endpoint_a", limit)

        # Endpoint B should still be allowed
        result = limiter.check_limit("ip:1.2.3.4", "/endpoint_b", limit)
        assert result.allowed is True

    def test_token_and_ip_keys_are_independent(self):
        """Token keys and IP keys must be tracked independently."""
        limiter = InMemoryRateLimiter()
        limit = (2, 60)

        # Make 2 requests with token
        limiter.check_limit("token:tw_supp_test1", "/test", limit)
        limiter.check_limit("token:tw_supp_test1", "/test", limit)

        # Same IP should still be allowed (different key)
        result = limiter.check_limit("ip:1.2.3.4", "/test", limit)
        assert result.allowed is True

    def test_cleanup_removes_old_entries(self):
        """Expired entries must be purged to prevent memory leaks."""
        limiter = InMemoryRateLimiter()
        limit = (2, 1)  # 1 second window

        limiter.check_limit("ip:1.2.3.4", "/test", limit)
        limiter.check_limit("ip:1.2.3.4", "/test", limit)
        
        # Should be limited now
        result = limiter.check_limit("ip:1.2.3.4", "/test", limit)
        assert result.allowed is False

        # Wait for the window to expire
        time.sleep(1.1)

        # After cleanup, should be allowed again
        result = limiter.check_limit("ip:1.2.3.4", "/test", limit)
        assert result.allowed is True


    def test_backend_type_returns_correct_string(self):
        """backend_type should return 'memory' for in-memory limiter."""
        limiter = InMemoryRateLimiter()
        assert limiter.backend_type == "memory"
