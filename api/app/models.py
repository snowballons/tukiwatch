from typing import Any

from pydantic import BaseModel


class BatchRequest(BaseModel):
    urls: list[str]


class StreamStatus(BaseModel):
    url: str
    status: str
    title: str = ""
    author: str = ""
    thumbnail: str = ""
    error: str = ""
    # Enhanced metadata
    category: str = ""
    stream_id: str = ""
    platform: str = ""
    error_details: dict[str, Any] | None = None


class StreamResolution(BaseModel):
    status: str
    title: str | None = None
    author: str | None = None
    thumbnail: str | None = None
    best_quality: str | None = None
    all_qualities: dict[str, str] | None = None
    error: str | None = None
    original_url: str | None = None
    # Enhanced metadata
    category: str | None = None
    stream_id: str | None = None
    platform: str | None = None
    stream_types: list[str] | None = None
    error_details: dict[str, Any] | None = None


# Discovery models for Twitch Helix API
class DiscoveryStream(BaseModel):
    """Stream data from Twitch Helix Get Streams endpoint"""

    id: str
    user_id: str
    user_login: str
    user_name: str
    game_id: str
    game_name: str
    type: str
    title: str
    viewer_count: int
    started_at: str
    language: str
    thumbnail_url: str
    tag_ids: list[str] = []
    is_mature: bool = False


class DiscoveryPagination(BaseModel):
    cursor: str | None = None


class DiscoveryResponse(BaseModel):
    data: list[DiscoveryStream]
    pagination: DiscoveryPagination | None = None


class DiscoveryFilters(BaseModel):
    """Query parameters for discovery endpoint"""

    platform: str = "twitch"
    game_id: str | None = None
    game_name: str | None = None
    language: str | None = None
    sort_by: str = "viewer_count"  # viewer_count, started_at
    limit: int = 20  # max 100
    cursor: str | None = None


class GameCategory(BaseModel):
    id: str
    name: str
    box_art_url: str


class GameCategoriesResponse(BaseModel):
    data: list[GameCategory]
    pagination: DiscoveryPagination | None = None
