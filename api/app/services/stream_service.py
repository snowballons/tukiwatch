import asyncio
import streamlink
from streamlink.session import Streamlink
from config import config
from app.models import StreamStatus

async def check_single_stream(url: str) -> StreamStatus:
    """Check status of a single stream URL asynchronously"""
    try:
        result = await asyncio.to_thread(_resolve_stream_sync, url)
        return result
    except Exception as e:
        return StreamStatus(
            url=url,
            status="error",
            error=str(e)
        )

def _resolve_stream_sync(url: str) -> StreamStatus:
    """Synchronous streamlink resolution"""
    try:
        session = Streamlink()
        session.set_option("webbrowser-executable", config.CHROME_PATH)
        session.set_option("http-headers", "User-Agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36")

        plugin_name, plugin_class, resolved_url = session.resolve_url(url)
        plugin_instance = plugin_class(session, resolved_url)
        streams = plugin_instance.streams()
        
        if not streams:
            return StreamStatus(url=url, status="offline")

        metadata = plugin_instance.get_metadata()
        thumb = metadata.get("thumbnail") or ""
        
        if thumb and "{width}" in thumb:
            thumb = thumb.replace("{width}", "800").replace("{height}", "450")

        return StreamStatus(
            url=url,
            status="online",
            title=metadata.get("title") or "Live Stream",
            author=metadata.get("author") or plugin_name,
            thumbnail=thumb
        )
    except Exception as e:
        return StreamStatus(url=url, status="offline", error=str(e))

def resolve_stream_details(url: str):
    """
    Get full stream details including playback URLs.
    This was previously embedded in the /resolve endpoint.
    """
    try:
        session = Streamlink()
        session.set_option("webbrowser-executable", config.CHROME_PATH)
        session.set_option("http-headers", "User-Agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36")

        try:
            plugin_name, plugin_class, resolved_url = session.resolve_url(url)
            plugin_instance = plugin_class(session, resolved_url)
            streams = plugin_instance.streams()
            
            if not streams:
                return {"status": "offline", "original_url": url}

            metadata = plugin_instance.get_metadata()
            thumb = metadata.get("thumbnail") or ""

            if thumb and "{width}" in thumb:
                thumb = thumb.replace("{width}", "800").replace("{height}", "450")

            return {
                "status": "online",
                "title": metadata.get("title") or "Live Stream",
                "author": metadata.get("author") or plugin_name,
                "thumbnail": thumb,
                "best_quality": streams.get("best").url,
                "all_qualities": {name: s.url for name, s in streams.items()}
            }

        except Exception as e:
            error_msg = str(e)
            if "Chromium-based web browser" in error_msg:
                raise ValueError("Server missing Chromium browser. Kick.com requires a browser to bypass Cloudflare.")
            return {"status": "offline", "error": error_msg}

    except Exception as e:
        raise e
