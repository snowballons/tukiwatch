from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from app.auth import get_client_key, get_supporter_info
from app.rate_limit import RateLimitConfig, create_rate_limit_error
from app.rate_limiter import rate_limiter_factory


class CustomRateLimitMiddleware(BaseHTTPMiddleware):
    """Custom rate limiting middleware with per-endpoint limits and supporter tier support."""

    def __init__(self, app):
        super().__init__(app)
        # Initialize the rate limiter (Redis or in-memory fallback)
        self._limiter = rate_limiter_factory.create()

    async def dispatch(self, request: Request, call_next):
        """Process request with rate limiting."""
        endpoint = request.url.path

        # Skip excluded paths entirely (no rate limiting, no headers)
        if RateLimitConfig.is_excluded(endpoint):
            return await call_next(request)

        # Extract supporter info
        supporter_info = await get_supporter_info(request)
        is_supporter = supporter_info["is_supporter"]
        tier = supporter_info["tier"]

        # Get token from headers for client key generation
        token = request.headers.get("X-Supporter-Token")

        # Generate client key (token:xxx or ip:xxx.xxx)
        client_key = get_client_key(request, is_supporter, token)
        # Get rate limit for this endpoint and tier
        limit = RateLimitConfig.get_limit_for_path(endpoint, is_supporter)

        # Check rate limit
        result = self._limiter.check_limit(client_key, endpoint, limit)

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

        # Add supporter tier header if supporter
        if is_supporter:
            response.headers["X-Supporter-Tier"] = tier

        return response
