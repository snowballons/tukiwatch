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
