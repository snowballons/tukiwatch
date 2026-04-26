"""
Tests for main.py

Covers:
- GET /          → status ok
- GET /health    → status healthy
- GET /cache/stats
- GET /rate-limit/stats
- GET /session/stats
- App initialisation: middleware stack, router registration
"""

from unittest.mock import patch


from tests.conftest import TEST_API_KEY

AUTH = {"X-API-Key": TEST_API_KEY}


# ===========================================================================
# Root & health endpoints (no auth required)
# ===========================================================================


class TestRootEndpoint:
    """Tests for the GET / endpoint."""

    def test_returns_200(self, client):
        """GET / must return HTTP 200."""
        response = client.get("/")
        assert response.status_code == 200

    def test_returns_status_ok(self, client):
        """GET / must include status=ok in the response body."""
        response = client.get("/")
        data = response.json()
        assert data["status"] == "ok"

    def test_returns_service_name(self, client):
        """GET / must include the service name in the response body."""
        response = client.get("/")
        data = response.json()
        assert "service" in data
        assert data["service"] == "streamlink-api"

    def test_does_not_require_api_key(self, client):
        """GET / must be accessible without an API key."""
        response = client.get("/")
        assert response.status_code == 200


class TestHealthEndpoint:
    """Tests for the GET /health endpoint."""

    def test_returns_200(self, client):
        """GET /health must return HTTP 200."""
        response = client.get("/health")
        assert response.status_code == 200

    def test_returns_healthy_status(self, client):
        """GET /health must include status=healthy."""
        response = client.get("/health")
        data = response.json()
        assert data["status"] == "healthy"

    def test_returns_service_name(self, client):
        """GET /health must include the service name."""
        response = client.get("/health")
        data = response.json()
        assert data["service"] == "streamlink-api"

    def test_does_not_require_api_key(self, client):
        """GET /health must be accessible without an API key."""
        response = client.get("/health")
        assert response.status_code == 200


# ===========================================================================
# /cache/stats
# ===========================================================================


class TestCacheStatsEndpoint:
    """Tests for the GET /cache/stats endpoint."""

    def test_returns_200(self, client):
        """GET /cache/stats must return HTTP 200."""
        with patch("app.cache.cache") as mock_cache:
            mock_cache.get_stats.return_value = {"type": "SimpleCache", "keys": 0}
            response = client.get("/cache/stats")
        assert response.status_code == 200

    def test_returns_cache_key_in_body(self, client):
        """GET /cache/stats must include a 'cache' key in the response."""
        with patch("app.cache.cache") as mock_cache:
            mock_cache.get_stats.return_value = {"type": "SimpleCache", "keys": 5}
            response = client.get("/cache/stats")
        data = response.json()
        assert "cache" in data

    def test_returns_service_name(self, client):
        """GET /cache/stats must include the service name."""
        with patch("app.cache.cache") as mock_cache:
            mock_cache.get_stats.return_value = {}
            response = client.get("/cache/stats")
        assert response.json()["service"] == "streamlink-api"

    def test_does_not_require_api_key(self, client):
        """GET /cache/stats must be accessible without an API key."""
        with patch("app.cache.cache") as mock_cache:
            mock_cache.get_stats.return_value = {}
            response = client.get("/cache/stats")
        assert response.status_code == 200


# ===========================================================================
# /rate-limit/stats
# ===========================================================================


class TestRateLimitStatsEndpoint:
    """Tests for the GET /rate-limit/stats endpoint."""

    def test_returns_200(self, client):
        """GET /rate-limit/stats must return HTTP 200."""
        response = client.get("/rate-limit/stats")
        assert response.status_code == 200

    def test_returns_rate_limits_key(self, client):
        """GET /rate-limit/stats must include a 'rate_limits' key."""
        response = client.get("/rate-limit/stats")
        data = response.json()
        assert "rate_limits" in data

    def test_rate_limits_includes_resolve(self, client):
        """The rate_limits dict must document the /resolve endpoint limit."""
        response = client.get("/rate-limit/stats")
        data = response.json()
        assert "resolve" in data["rate_limits"]

    def test_rate_limits_includes_status_batch(self, client):
        """The rate_limits dict must document the /status-batch endpoint limit."""
        response = client.get("/rate-limit/stats")
        data = response.json()
        assert "status_batch" in data["rate_limits"]

    def test_returns_service_name(self, client):
        """GET /rate-limit/stats must include the service name."""
        response = client.get("/rate-limit/stats")
        assert response.json()["service"] == "streamlink-api"

    def test_does_not_require_api_key(self, client):
        """GET /rate-limit/stats must be accessible without an API key."""
        response = client.get("/rate-limit/stats")
        assert response.status_code == 200


# ===========================================================================
# /session/stats
# ===========================================================================


class TestSessionStatsEndpoint:
    """Tests for the GET /session/stats endpoint."""

    def test_returns_200(self, client):
        """GET /session/stats must return HTTP 200."""
        with patch("app.session_pool.session_pool") as mock_pool:
            mock_pool.size.return_value = 3
            mock_pool.pool_size = 3
            mock_pool.created_at = 0.0
            mock_pool.refresh_interval = 3600
            response = client.get("/session/stats")
        assert response.status_code == 200

    def test_returns_session_pool_key(self, client):
        """GET /session/stats must include a 'session_pool' key."""
        with patch("app.session_pool.session_pool") as mock_pool:
            mock_pool.size.return_value = 3
            mock_pool.pool_size = 3
            mock_pool.created_at = 0.0
            mock_pool.refresh_interval = 3600
            response = client.get("/session/stats")
        data = response.json()
        assert "session_pool" in data

    def test_session_pool_includes_available_sessions(self, client):
        """The session_pool dict must include available_sessions."""
        with patch("app.session_pool.session_pool") as mock_pool:
            mock_pool.size.return_value = 2
            mock_pool.pool_size = 3
            mock_pool.created_at = 0.0
            mock_pool.refresh_interval = 3600
            response = client.get("/session/stats")
        data = response.json()["session_pool"]
        assert "available_sessions" in data
        assert data["available_sessions"] == 2

    def test_returns_service_name(self, client):
        """GET /session/stats must include the service name."""
        with patch("app.session_pool.session_pool") as mock_pool:
            mock_pool.size.return_value = 3
            mock_pool.pool_size = 3
            mock_pool.created_at = 0.0
            mock_pool.refresh_interval = 3600
            response = client.get("/session/stats")
        assert response.json()["service"] == "streamlink-api"

    def test_does_not_require_api_key(self, client):
        """GET /session/stats must be accessible without an API key."""
        with patch("app.session_pool.session_pool") as mock_pool:
            mock_pool.size.return_value = 3
            mock_pool.pool_size = 3
            mock_pool.created_at = 0.0
            mock_pool.refresh_interval = 3600
            response = client.get("/session/stats")
        assert response.status_code == 200


# ===========================================================================
# App initialisation
# ===========================================================================


class TestAppInitialisation:
    """Tests that verify the FastAPI app is configured correctly."""

    def test_app_has_correct_title(self, app):
        """The FastAPI app title must be 'Streamlink API'."""
        assert app.title == "Streamlink API"

    def test_app_has_correct_version(self, app):
        """The FastAPI app version must be '1.0.0'."""
        assert app.version == "1.0.0"

    def test_api_routes_are_registered(self, app):
        """The /api/resolve and /api/status-batch routes must be registered."""
        paths = [route.path for route in app.routes]
        assert "/api/resolve" in paths
        assert "/api/status-batch" in paths

    def test_utility_routes_are_registered(self, app):
        """The /, /health, /cache/stats, /rate-limit/stats, /session/stats routes must exist."""
        paths = [route.path for route in app.routes]
        for expected in ["/", "/health", "/cache/stats", "/rate-limit/stats", "/session/stats"]:
            assert expected in paths, f"Route {expected!r} not found in {paths}"

    def test_middleware_stack_includes_api_key_middleware(self, app):
        """APIKeyMiddleware must be present in the middleware stack."""
        from app.middleware import APIKeyMiddleware

        middleware_classes = [m.cls for m in app.user_middleware]
        assert APIKeyMiddleware in middleware_classes

    def test_middleware_stack_includes_rate_limit_middleware(self, app):
        """CustomRateLimitMiddleware must be present in the middleware stack."""
        from app.middleware import CustomRateLimitMiddleware

        middleware_classes = [m.cls for m in app.user_middleware]
        assert CustomRateLimitMiddleware in middleware_classes
