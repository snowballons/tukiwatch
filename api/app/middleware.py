from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware
import time
from typing import Dict, Tuple
from app.rate_limit import RateLimitConfig, create_rate_limit_error


class CustomRateLimitMiddleware(BaseHTTPMiddleware):
    """Custom rate limiting middleware with per-endpoint limits"""

    def __init__(self, app):
        super().__init__(app)
        # In-memory storage: {ip: {endpoint: [(timestamp, count), ...]}}
        self.requests: Dict[str, Dict[str, list]] = {}
        self.cleanup_interval = 300  # Clean up old entries every 5 minutes
        self.last_cleanup = time.time()

    def _cleanup_old_entries(self):
        """Remove old entries to prevent memory leaks"""
        current_time = time.time()
        if current_time - self.last_cleanup < self.cleanup_interval:
            return

        # Remove entries older than 1 hour
        cutoff_time = current_time - 3600

        for ip in list(self.requests.keys()):
            for endpoint in list(self.requests[ip].keys()):
                self.requests[ip][endpoint] = [
                    (timestamp, count)
                    for timestamp, count in self.requests[ip][endpoint]
                    if timestamp > cutoff_time
                ]
                if not self.requests[ip][endpoint]:
                    del self.requests[ip][endpoint]

            if not self.requests[ip]:
                del self.requests[ip]

        self.last_cleanup = current_time

    def _get_client_ip(self, request: Request) -> str:
        """Extract client IP address"""
        # Check for forwarded headers (for reverse proxy setups)
        forwarded_for = request.headers.get("X-Forwarded-For")
        if forwarded_for:
            return forwarded_for.split(",")[0].strip()

        real_ip = request.headers.get("X-Real-IP")
        if real_ip:
            return real_ip

        # Fallback to direct client IP
        return request.client.host if request.client else "unknown"

    def _is_rate_limited(
        self, ip: str, endpoint: str, limit: Tuple[int, int]
    ) -> Tuple[bool, int]:
        """Check if request should be rate limited"""
        max_requests, time_window = limit
        current_time = time.time()
        window_start = current_time - time_window

        # Initialize tracking for this IP/endpoint if needed
        if ip not in self.requests:
            self.requests[ip] = {}
        if endpoint not in self.requests[ip]:
            self.requests[ip][endpoint] = []

        # Remove old entries outside the time window
        self.requests[ip][endpoint] = [
            (timestamp, count)
            for timestamp, count in self.requests[ip][endpoint]
            if timestamp > window_start
        ]

        # Count current requests in the time window
        current_count = sum(count for _, count in self.requests[ip][endpoint])

        # Check if limit exceeded
        if current_count >= max_requests:
            # Calculate retry after time
            oldest_request = min(self.requests[ip][endpoint], key=lambda x: x[0])[0]
            retry_after = int(oldest_request + time_window - current_time) + 1
            return True, max(retry_after, 1)

        # Add current request
        self.requests[ip][endpoint].append((current_time, 1))
        return False, 0

    async def dispatch(self, request: Request, call_next):
        """Process request with rate limiting"""
        # Cleanup old entries periodically
        self._cleanup_old_entries()

        # Get client info
        client_ip = self._get_client_ip(request)
        endpoint = request.url.path

        # Get rate limit for this endpoint
        limit = RateLimitConfig.get_limit_for_path(endpoint)

        # Check rate limit
        is_limited, retry_after = self._is_rate_limited(client_ip, endpoint, limit)

        if is_limited:
            # Return rate limit error
            error_response = create_rate_limit_error(retry_after)
            return JSONResponse(
                status_code=error_response.status_code,
                content=error_response.detail,
                headers=error_response.headers,
            )

        # Process request normally
        response = await call_next(request)

        # Add rate limit headers to response
        max_requests, time_window = limit
        current_count = sum(
            count for _, count in self.requests.get(client_ip, {}).get(endpoint, [])
        )

        response.headers["X-RateLimit-Limit"] = str(max_requests)
        response.headers["X-RateLimit-Remaining"] = str(
            max(0, max_requests - current_count)
        )
        response.headers["X-RateLimit-Reset"] = str(int(time.time() + time_window))

        return response
