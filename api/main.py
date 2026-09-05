import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.middleware import CustomRateLimitMiddleware
from app.rate_limiter import rate_limiter_factory
from app.routers import streams
from config import config

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(levelname)s:     %(message)s",
)

logger = logging.getLogger(__name__)

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

app.include_router(streams.router, prefix="/api")


@app.get("/")
def read_root():
    return {"status": "ok", "service": "streamlink-api"}


@app.get("/health")
def health_check():
    return {"status": "healthy", "service": "streamlink-api"}


@app.get("/cache/stats")
def cache_stats():
    from app.cache import cache

    return {"cache": cache.get_stats(), "service": "streamlink-api"}


@app.get("/rate-limit/stats")
def rate_limit_stats():
    """Get rate limiting statistics"""
    limiter_stats = rate_limiter_factory.get_stats()

    return {
        "rate_limits": {
            "resolve": "20 requests per minute",
            "status_batch": "10 requests per minute",
            "default": "100 requests per minute",
            "health": "200 requests per minute",
        },
        "backend": limiter_stats.get("backend_type", "unknown"),
        "redis_connected": limiter_stats.get("redis_connected", False),
        "service": "streamlink-api",
        **limiter_stats,
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
