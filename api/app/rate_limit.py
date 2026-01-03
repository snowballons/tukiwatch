from fastapi import HTTPException
from typing import Tuple


class RateLimitConfig:
    """Rate limiting configuration for different endpoints"""

    # Rate limits: (requests, time_window_seconds)
    LIMITS = {
        # General API endpoints
        "default": (100, 60),  # 100 requests per minute
        # Stream-specific endpoints (more restrictive)
        "/resolve": (20, 60),  # 20 requests per minute for stream resolution
        "/status-batch": (10, 60),  # 10 requests per minute for batch status
        # Utility endpoints (more permissive)
        "/health": (200, 60),  # 200 requests per minute for health checks
        "/cache/stats": (50, 60),  # 50 requests per minute for cache stats
    }

    @classmethod
    def get_limit_for_path(cls, path: str) -> Tuple[int, int]:
        """Get rate limit for specific path"""
        # Check for exact match first
        if path in cls.LIMITS:
            return cls.LIMITS[path]

        # Check for path prefixes
        for limit_path, limit in cls.LIMITS.items():
            if path.startswith(limit_path):
                return limit

        # Return default limit
        return cls.LIMITS["default"]


def create_rate_limit_error(retry_after: int = 60) -> HTTPException:
    """Create a mobile-friendly rate limit error response"""
    return HTTPException(
        status_code=429,
        detail={
            "error": "Rate limit exceeded",
            "message": "Too many requests. Please try again later.",
            "retry_after": retry_after,
            "type": "rate_limit_error",
        },
        headers={"Retry-After": str(retry_after)},
    )
