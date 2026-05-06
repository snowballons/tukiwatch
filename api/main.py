from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import logging
from contextlib import asynccontextmanager
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from app.routers import streams
from app.middleware import APIKeyMiddleware, CustomRateLimitMiddleware
from app.services.liveness_worker import check_community_liveness
from config import config

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(levelname)s:     %(message)s",
)

# Suppress Streamlink plugin warnings globally
logging.getLogger("streamlink").setLevel(logging.ERROR)
logging.getLogger("streamlink.session.plugins").setLevel(logging.CRITICAL)

# Initialize scheduler
scheduler = AsyncIOScheduler()


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: Start the scheduler
    logging.info("Starting background scheduler...")
    scheduler.add_job(
        check_community_liveness, "interval", hours=1, id="community_liveness"
    )
    scheduler.start()

    # Optionally trigger an initial check on startup
    # scheduler.add_job(check_community_liveness, id="initial_check")

    yield

    # Shutdown: Stop the scheduler
    logging.info("Shutting down background scheduler...")
    scheduler.shutdown()


app = FastAPI(title="Streamlink API", version="1.0.0", lifespan=lifespan)

# Add API key authentication middleware (outermost — runs first)
app.add_middleware(APIKeyMiddleware)

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
