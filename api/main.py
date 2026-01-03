from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import logging
from app.routers import streams
from app.middleware import CustomRateLimitMiddleware
from config import config

# Suppress Streamlink plugin warnings globally
logging.getLogger("streamlink").setLevel(logging.ERROR)
logging.getLogger("streamlink.session.plugins").setLevel(logging.CRITICAL)

app = FastAPI(title="Streamlink API", version="1.0.0")

# Add rate limiting middleware (before CORS)
app.add_middleware(CustomRateLimitMiddleware)

# Configure CORS for mobile app
app.add_middleware(
    CORSMiddleware,
    allow_origins=config.ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

app.include_router(streams.router)


@app.get("/")
def read_root():
    return {"status": "ok", "service": "streamlink-api"}


@app.get("/health")
def health_check():
    return {"status": "healthy", "service": "streamlink-api"}


@app.get("/cache/stats")
def cache_stats():
    from app.cache import cache

    return {"cache_size": cache.size(), "service": "streamlink-api"}


@app.get("/rate-limit/stats")
def rate_limit_stats():
    """Get rate limiting statistics"""
    from app.middleware import CustomRateLimitMiddleware

    # Find the rate limit middleware instance
    for middleware in app.user_middleware:
        if isinstance(middleware.cls, type) and issubclass(
            middleware.cls, CustomRateLimitMiddleware
        ):
            # This is a bit tricky to access the instance, so we'll return general info
            break

    return {
        "rate_limits": {
            "resolve": "20 requests per minute",
            "status_batch": "10 requests per minute",
            "default": "100 requests per minute",
            "health": "200 requests per minute",
        },
        "service": "streamlink-api",
    }


@app.get("/session/stats")
def session_stats():
    """Get session pool statistics"""
    from app.session_pool import session_pool

    return {
        "session_pool": {
            "available_sessions": session_pool.size(),
            "pool_size": session_pool.pool_size,
            "created_at": session_pool.created_at,
            "refresh_interval": session_pool.refresh_interval,
        },
        "service": "streamlink-api",
    }
