from pydantic import BaseModel
from typing import List, Optional, Dict, Any


class BatchRequest(BaseModel):
    urls: List[str]


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
    error_details: Optional[Dict[str, Any]] = None


class StreamResolution(BaseModel):
    status: str
    title: Optional[str] = None
    author: Optional[str] = None
    thumbnail: Optional[str] = None
    best_quality: Optional[str] = None
    all_qualities: Optional[Dict[str, str]] = None
    error: Optional[str] = None
    original_url: Optional[str] = None
    # Enhanced metadata
    category: Optional[str] = None
    stream_id: Optional[str] = None
    platform: Optional[str] = None
    stream_types: Optional[List[str]] = None
    error_details: Optional[Dict[str, Any]] = None
