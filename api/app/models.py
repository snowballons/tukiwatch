from pydantic import BaseModel
from typing import List

class BatchRequest(BaseModel):
    urls: List[str]

class StreamStatus(BaseModel):
    url: str
    status: str
    title: str = ""
    author: str = ""
    thumbnail: str = ""
    error: str = ""
