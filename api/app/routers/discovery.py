"""Discovery router for Twitch Helix API endpoints"""

import asyncio
import logging
from typing import Any

from fastapi import APIRouter, HTTPException, Query

from app.cache import cache
from app.models import (
    DiscoveryFilters,
    DiscoveryResponse,
    DiscoveryStream,
    GameCategoriesResponse,
    GameCategory,
)
from app.services.twitch_helix import get_twitch_client

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/discover", tags=["discovery"])

# Cache TTL for discovery data (1 hour)
CACHE_TTL = 3600  # 1 hour


def _cache_key(*parts: str) -> str:
    """Generate cache key"""
    return "discover:" + ":".join(parts)


def _transform_stream(stream: dict[str, Any]) -> DiscoveryStream:
    """Transform Twitch Helix stream response to DiscoveryStream model"""
    return DiscoveryStream(
        id=stream["id"],
        user_id=stream["user_id"],
        user_login=stream["user_login"],
        user_name=stream["user_name"],
        game_id=stream["game_id"],
        game_name=stream["game_name"],
        type=stream["type"],
        title=stream["title"],
        viewer_count=stream["viewer_count"],
        started_at=stream["started_at"],
        language=stream["language"],
        thumbnail_url=stream.get("thumbnail_url", ""),
        tag_ids=stream.get("tag_ids", []),
        is_mature=stream.get("is_mature", False),
    )


def _transform_game(game: dict[str, Any]) -> GameCategory:
    """Transform Twitch Helix category to GameCategory model"""
    return GameCategory(
        id=game["id"],
        name=game["name"],
        box_art_url=game.get("box_art_url", ""),
    )


@router.get("/twitch/streams")
async def discover_twitch_streams(
    game_id: str | None = Query(default=None, description="Filter by game/category ID"),
    game_name: str | None = Query(default=None, description="Filter by game name"),
    language: str | None = Query(
        default=None, description="Filter by language (e.g., 'en')"
    ),
    sort_by: str = Query(
        default="viewer_count", description="Sort by: viewer_count or started_at"
    ),
    limit: int = Query(
        default=20, ge=1, le=100, description="Number of streams (max 100)"
    ),
    cursor: str | None = Query(default=None, description="Pagination cursor"),
):
    """Get live Twitch streams for discovery

    This endpoint fetches live streams from Twitch Helix API with optional
    filtering and pagination. Results are cached for 1 hour.

    Args:
        game_id: Filter by specific game/category ID
        game_name: Filter by game name (searches top games)
        language: Filter by stream language
        sort_by: Sort order (viewer_count desc, started_at asc)
        limit: Number of streams (1-100, default 20)
        cursor: Pagination cursor from previous response

    Returns:
        DiscoveryResponse with streams and pagination info
    """
    # Build cache key
    cache_params = {
        "game_id": game_id,
        "language": language,
        "limit": limit,
        "cursor": cursor,
    }
    cache_key = _cache_key(
        "twitch", "streams", ",".join(f"{k}={v}" for k, v in cache_params.items() if v)
    )

    # Check cache
    cached = cache.get(cache_key)
    if cached:
        logger.debug(f"Cache hit for {cache_key}")
        return cached

    # Get client and fetch from Twitch
    try:
        twitch_client = get_twitch_client()
        params: dict[str, Any] = {"first": limit}
        if game_id:
            params["game_id"] = game_id
        if language:
            params["language"] = language
        if cursor:
            params["after"] = cursor

        response = await asyncio.to_thread(twitch_client.get_streams, **params)
    except Exception as e:
        logger.error(f"Failed to fetch Twitch streams: {e}")
        raise HTTPException(
            status_code=503, detail="Failed to fetch streams from Twitch"
        )

    # Transform data
    streams = [_transform_stream(s) for s in response.get("data", [])]
    pagination = response.get("pagination", {})

    result = DiscoveryResponse(
        data=streams,
        pagination={"cursor": pagination.get("cursor")}
        if pagination.get("cursor")
        else None,
    )

    # Cache result for 1 hour
    cache.set(cache_key, result.model_dump(), ttl=CACHE_TTL)

    return result


@router.get("/twitch/streams/top")
async def discover_twitch_top_streams(
    limit: int = Query(
        default=20, ge=1, le=100, description="Number of streams (max 100)"
    ),
    cursor: str | None = Query(default=None, description="Pagination cursor"),
):
    """Get top live streams by viewer count

    This is a convenience endpoint for the default top streams discovery.

    Returns:
        DiscoveryResponse with top streams and pagination info
    """
    cache_key = _cache_key(
        "twitch", "top", f"limit={limit}", f"cursor={cursor or 'none'}"
    )

    cached = cache.get(cache_key)
    if cached:
        logger.debug(f"Cache hit for {cache_key}")
        return cached

    try:
        twitch_client = get_twitch_client()
        response = await asyncio.to_thread(
            twitch_client.get_streams,
            first=limit,
            after=cursor,
            sort_by="viewer_count",
        )
    except Exception as e:
        logger.error(f"Failed to fetch top Twitch streams: {e}")
        raise HTTPException(
            status_code=503, detail="Failed to fetch streams from Twitch"
        )

    streams = [_transform_stream(s) for s in response.get("data", [])]
    pagination = response.get("pagination", {})

    result = DiscoveryResponse(
        data=streams,
        pagination={"cursor": pagination.get("cursor")}
        if pagination.get("cursor")
        else None,
    )

    cache.set(cache_key, result.model_dump(), ttl=CACHE_TTL)
    return result


@router.get("/twitch/games")
async def discover_twitch_games(
    limit: int = Query(
        default=20, ge=1, le=100, description="Number of games (max 100)"
    ),
    cursor: str | None = Query(default=None, description="Pagination cursor"),
):
    """Get top games/categories on Twitch

    Returns:
        GameCategoriesResponse with list of popular games and pagination
    """
    cache_key = _cache_key(
        "twitch", "games", f"limit={limit}", f"cursor={cursor or 'none'}"
    )

    cached = cache.get(cache_key)
    if cached:
        logger.debug(f"Cache hit for {cache_key}")
        return cached

    try:
        twitch_client = get_twitch_client()
        response = await asyncio.to_thread(
            twitch_client.get_top_games,
            first=limit,
            after=cursor,
        )
    except Exception as e:
        logger.error(f"Failed to fetch Twitch games: {e}")
        raise HTTPException(status_code=503, detail="Failed to fetch games from Twitch")

    categories = [_transform_game(g) for g in response.get("data", [])]
    pagination = response.get("pagination", {})

    result = GameCategoriesResponse(
        data=categories,
        pagination={"cursor": pagination.get("cursor")}
        if pagination.get("cursor")
        else None,
    )

    cache.set(cache_key, result.model_dump(), ttl=CACHE_TTL)
    return result


@router.get("/twitch/search")
async def search_twitch_categories(
    query: str = Query(..., min_length=1, description="Search query"),
    limit: int = Query(default=20, ge=1, le=100, description="Number of results"),
):
    """Search for Twitch categories/games

    Returns:
        List of matching game categories
    """
    cache_key = _cache_key("twitch", "search", query[:50], f"limit={limit}")

    cached = cache.get(cache_key)
    if cached:
        logger.debug(f"Cache hit for {cache_key}")
        return cached

    try:
        twitch_client = get_twitch_client()
        response = await asyncio.to_thread(
            twitch_client.search_categories,
            query=query,
            first=limit,
        )
    except Exception as e:
        logger.error(f"Failed to search Twitch categories: {e}")
        raise HTTPException(status_code=503, detail="Failed to search categories")

    categories = [_transform_game(c) for c in response.get("categories", [])]

    result = GameCategoriesResponse(data=categories)
    cache.set(cache_key, result.model_dump(), ttl=CACHE_TTL)
    return result


@router.post("/twitch/streams/filter")
async def filter_twitch_streams(
    filters: DiscoveryFilters,
):
    """Filter streams by multiple criteria

    This endpoint allows filtering by game, language, and other criteria.
    Uses cache for 1 hour.

    Args:
        filters: DiscoveryFilters with platform, game_id, language, etc.

    Returns:
        DiscoveryResponse with filtered streams
    """
    # Build cache key from filters
    cache_params = {
        "game_id": filters.game_id,
        "language": filters.language,
        "limit": filters.limit,
        "cursor": filters.cursor,
    }
    cache_key = _cache_key(
        "twitch", "filter", ",".join(f"{k}={v}" for k, v in cache_params.items() if v)
    )

    cached = cache.get(cache_key)
    if cached:
        logger.debug(f"Cache hit for {cache_key}")
        return cached

    try:
        twitch_client = get_twitch_client()
        params: dict[str, Any] = {"first": filters.limit}
        if filters.game_id:
            params["game_id"] = filters.game_id
        if filters.language:
            params["language"] = filters.language
        if filters.cursor:
            params["after"] = filters.cursor

        response = await asyncio.to_thread(twitch_client.get_streams, **params)
    except Exception as e:
        logger.error(f"Failed to filter Twitch streams: {e}")
        raise HTTPException(
            status_code=503, detail="Failed to fetch streams from Twitch"
        )

    streams = [_transform_stream(s) for s in response.get("data", [])]
    pagination = response.get("pagination", {})

    result = DiscoveryResponse(
        data=streams,
        pagination={"cursor": pagination.get("cursor")}
        if pagination.get("cursor")
        else None,
    )

    cache.set(cache_key, result.model_dump(), ttl=CACHE_TTL)
    return result
