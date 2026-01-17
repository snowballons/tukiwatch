from fastapi import HTTPException


class StreamlinkAPIException(HTTPException):
    """Base exception for Streamlink API errors"""

    pass


class NoPluginException(StreamlinkAPIException):
    def __init__(self, url: str):
        super().__init__(status_code=400, detail=f"No plugin available for URL: {url}")


class NoStreamsException(StreamlinkAPIException):
    def __init__(self, url: str):
        super().__init__(status_code=404, detail=f"No streams found for URL: {url}")


class PluginException(StreamlinkAPIException):
    def __init__(self, url: str, error: str):
        super().__init__(status_code=422, detail=f"Plugin error for {url}: {error}")
