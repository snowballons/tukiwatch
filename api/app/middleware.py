
from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from app.rate_limit import RateLimitConfig, create_rate_limit_error
from app.rate_limiter import rate_limiter_factory


class CustomRateLimitMiddleware(BaseHTTPMiddleware):
    """Custom rate limiting middleware with per-endpoint limits."""

    def __init__(self, app):
        super().__init__(app)
        # Initialize the rate limiter (Redis or in-memory fallback)
        self._limiter = rate_limiter_factory.create()

    def _get_client_ip(self, request: Request) -> str:
        """Extract client IP address."""
        # Check for forwarded headers (for reverse proxy setups)
        forwarded_for = request.headers.get("X-Forwarded-For")
        if forwarded_for:
            return forwarded_for.split(",")[0].strip()

        real_ip = request.headers.get("X-Real-IP")
        if real_ip:
            return real_ip

        # Fallback to direct client IP
        return request.client.host if request.client else "unknown"

    async def dispatch(self, request: Request, call_next):
        """Process request with rate limiting."""
        # Get client info
        client_ip = self._get_client_ip(request)
        endpoint = request.url.path

        # Get rate limit for this endpoint
        limit = RateLimitConfig.get_limit_for_path(endpoint)

        # Check rate limit
        result = self._limiter.check_limit(client_ip, endpoint, limit)

        if not result.allowed:
            # Return rate limit error
            error_response = create_rate_limit_error(result.retry_after)
            return JSONResponse(
                status_code=error_response.status_code,
                content=error_response.detail,
                headers=error_response.headers,
            )

        # Process request normally
        response = await call_next(request)

        # Add rate limit headers to response
        response.headers["X-RateLimit-Limit"] = str(result.limit)
        response.headers["X-RateLimit-Remaining"] = str(result.remaining)
        response.headers["X-RateLimit-Reset"] = str(result.reset_time)

        return response