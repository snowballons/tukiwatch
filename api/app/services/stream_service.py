import asyncio
import logging
from streamlink.exceptions import NoPluginError, NoStreamsError, PluginError
from config import config
from app.models import StreamStatus
from app.exceptions import (
    NoPluginException,
    NoStreamsException,
    PluginException,
    BrowserRequiredException,
)
from app.cache import cache
from app.utils import (
    extract_platform_from_url,
    generate_fallback_thumbnail,
    get_stream_types_from_streams,
)
from app.session_pool import session_pool

# Suppress Streamlink plugin loading warnings more aggressively
logging.getLogger("streamlink.session.plugins").setLevel(logging.CRITICAL)
logging.getLogger("streamlink.plugins").setLevel(logging.CRITICAL)
logging.getLogger("streamlink").setLevel(logging.ERROR)


async def check_single_stream(url: str) -> StreamStatus:
    """Check status of a single stream URL asynchronously"""
    # Check cache first (shorter TTL for status checks)
    cache_key = f"status:{url}"
    cached_result = cache.get(cache_key)
    if cached_result:
        # Add cache indicator to cached results
        if hasattr(cached_result, "__dict__"):
            cached_result.__dict__["_cached"] = True
        return cached_result

    try:
        result = await asyncio.to_thread(_resolve_stream_sync, url)
        # Cache status for 2 minutes
        cache.set(cache_key, result, ttl=120)
        return result
    except Exception as e:
        error_result = StreamStatus(url=url, status="error", error=str(e))
        # Cache errors for shorter time (30 seconds)
        cache.set(cache_key, error_result, ttl=30)
        return error_result


def _resolve_stream_sync(url: str) -> StreamStatus:
    """Synchronous streamlink resolution using session pool"""
    session = session_pool.get_session()

    try:
        # Twitch-specific optimizations
        platform = extract_platform_from_url(url)
        if platform == "twitch":
            try:
                # Enable higher quality streams (h264, h265, av1)
                session.set_option("twitch-supported-codecs", "h264,h265,av1")
                # Enable low latency streaming for better performance
                session.set_option("twitch-low-latency", True)
                # Add OAuth token if available (for ad-free streams)
                if config.TWITCH_OAUTH_TOKEN:
                    session.set_option(
                        "twitch-api-header",
                        f"Authorization=OAuth {config.TWITCH_OAUTH_TOKEN}",
                    )
            except Exception:
                # Ignore Twitch-specific option errors, continue with basic functionality
                pass

        plugin_name, plugin_class, resolved_url = session.resolve_url(url)
        plugin_instance = plugin_class(session, resolved_url)
        streams = plugin_instance.streams()

        if not streams:
            return StreamStatus(url=url, status="offline", platform=platform)

        metadata = plugin_instance.get_metadata()
        author = metadata.get("author") or plugin_name

        return StreamStatus(
            url=url,
            status="online",
            title=metadata.get("title") or "Live Stream",
            author=author,
            thumbnail=generate_fallback_thumbnail(platform, author),
            category=metadata.get("category") or "",
            stream_id=metadata.get("id") or "",
            platform=platform,
        )
    except NoPluginError:
        return StreamStatus(
            url=url,
            status="error",
            error="No plugin available for this URL",
            platform=extract_platform_from_url(url),
        )
    except NoStreamsError:
        return StreamStatus(
            url=url,
            status="offline",
            error="No streams available",
            platform=extract_platform_from_url(url),
        )
    except PluginError as e:
        error_msg = str(e)
        platform = extract_platform_from_url(url)
        
        # Check for browser-related errors
        if any(keyword in error_msg.lower() for keyword in [
            "chromium-based web browser", 
            "403 client error: forbidden",
            "browser", 
            "cloudflare"
        ]):
            return StreamStatus(
                url=url,
                status="error",
                error="Browser dependency required",
                platform=platform,
                error_details={
                    "type": "browser_required",
                    "message": f"{platform.title()} requires browser automation",
                    "reason": "Platform uses anti-bot protection"
                }
            )
        
        return StreamStatus(
            url=url,
            status="error",
            error=f"Plugin error: {error_msg}",
            platform=platform,
        )
    except Exception as e:
        return StreamStatus(
            url=url,
            status="error",
            error=f"Unexpected error: {str(e)}",
            platform=extract_platform_from_url(url),
        )
    finally:
        # Return session to pool
        session_pool.return_session(session)


def resolve_stream_details(url: str):
    """
    Get full stream details including playback URLs.
    This was previously embedded in the /resolve endpoint.
    """
    # Check cache first (longer TTL for full resolution)
    cache_key = f"resolve:{url}"
    cached_result = cache.get(cache_key)
    if cached_result:
        # Add cache indicator to cached results
        if isinstance(cached_result, dict):
            cached_result["_cached"] = True
        return cached_result

    session = session_pool.get_session()

    try:
        # Twitch-specific optimizations
        platform = extract_platform_from_url(url)
        if platform == "twitch":
            try:
                # Enable higher quality streams (h264, h265, av1)
                session.set_option("twitch-supported-codecs", "h264,h265,av1")
                # Enable low latency streaming for better performance
                session.set_option("twitch-low-latency", True)
                # Add OAuth token if available (for ad-free streams)
                if config.TWITCH_OAUTH_TOKEN:
                    session.set_option(
                        "twitch-api-header",
                        f"Authorization=OAuth {config.TWITCH_OAUTH_TOKEN}",
                    )
            except Exception:
                # Ignore Twitch-specific option errors, continue with basic functionality
                pass

        plugin_name, plugin_class, resolved_url = session.resolve_url(url)
        plugin_instance = plugin_class(session, resolved_url)
        streams = plugin_instance.streams()

        if not streams:
            result = {"status": "offline", "original_url": url, "platform": platform}
            cache.set(cache_key, result, ttl=60)  # Cache offline for 1 minute
            return result

        metadata = plugin_instance.get_metadata()
        author = metadata.get("author") or plugin_name

        result = {
            "status": "online",
            "title": metadata.get("title") or "Live Stream",
            "author": author,
            "thumbnail": generate_fallback_thumbnail(platform, author),
            "best_quality": streams.get("best").url,
            "all_qualities": {name: s.url for name, s in streams.items()},
            "category": metadata.get("category") or "",
            "stream_id": metadata.get("id") or "",
            "platform": platform,
            "stream_types": get_stream_types_from_streams(streams),
        }

        # Cache successful resolution for 5 minutes
        cache.set(cache_key, result, ttl=300)
        return result

    except NoPluginError:
        raise NoPluginException(url)
    except NoStreamsError:
        raise NoStreamsException(url)
    except PluginError as e:
        error_msg = str(e)
        
        # Check for browser-related errors and convert to BrowserRequiredException
        if any(keyword in error_msg.lower() for keyword in [
            "chromium-based web browser", 
            "403 client error: forbidden",
            "browser", 
            "cloudflare"
        ]):
            raise BrowserRequiredException(url)
            
        raise PluginException(url, error_msg)
    except Exception as e:
        raise PluginException(url, f"Unexpected error: {str(e)}")
    finally:
        # Return session to pool
        session_pool.return_session(session)
