from fastapi import HTTPException
from app.utils import extract_platform_from_url


BROWSER_ERROR_KEYWORDS = (
    "chromium-based web browser",
    "403 client error: forbidden",
    "browser",
    "cloudflare",
)


def is_browser_error(error_msg: str) -> bool:
    error_lower = error_msg.lower()
    return any(keyword in error_lower for keyword in BROWSER_ERROR_KEYWORDS)


class StreamlinkAPIException(HTTPException):
    """Base exception for Streamlink API errors"""

    pass


class NoPluginException(StreamlinkAPIException):
    def __init__(self, url: str):
        super().__init__(status_code=400, detail=f"No plugin available for URL: {url}")


class NoStreamsException(StreamlinkAPIException):
    def __init__(self, url: str):
        super().__init__(status_code=404, detail=f"No streams found for URL: {url}")


class BrowserRequiredException(StreamlinkAPIException):
    def __init__(self, url: str):
        platform = extract_platform_from_url(url)
        platform_info = {
            "kick": {
                "name": "Kick.com",
                "reason": "requires browser automation to bypass Cloudflare protection",
                "alternative": "Try using the official Kick.com website or mobile app",
            },
            "twitch": {
                "name": "Twitch",
                "reason": "may require browser for client-integrity tokens on some streams",
                "alternative": "Most Twitch streams work without browser requirements",
            },
        }

        info = platform_info.get(
            platform,
            {
                "name": platform.title(),
                "reason": "requires browser automation",
                "alternative": "Try using the official platform website or mobile app",
            },
        )

        super().__init__(
            status_code=422,
            detail={
                "error": "Browser dependency required",
                "platform": info["name"],
                "reason": f"This platform {info['reason']}",
                "message": "Browser automation is not available in this deployment",
                "alternative": info["alternative"],
                "url": url,
            },
        )


class PluginException(StreamlinkAPIException):
    def __init__(self, url: str, error: str):
        if is_browser_error(error):
            raise BrowserRequiredException(url)

        platform = extract_platform_from_url(url)

        super().__init__(
            status_code=422,
            detail={
                "error": "Plugin error",
                "platform": platform,
                "message": error,
                "url": url,
            },
        )
