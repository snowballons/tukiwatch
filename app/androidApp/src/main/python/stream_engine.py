"""Stream engine for TukiWatch (Android Chaquopy + desktop subprocess iteration).

Parity with tukiwatch-core (errors.py, metadata.py, resolution.py):
- Error taxonomy: NoPluginError, NoStreamsError, BrowserRequiredError, PluginError
- Metadata: extract_platform_from_url, parse_url_metadata, normalize_stream_metadata,
  generate_fallback_thumbnail, get_stream_types_from_streams, extract_category_from_json,
  extract_category_keywords, extract_viewer_count
- Pinned codec alignment: twitch-supported-codecs = 'h264,h265,av1', twitch-low-latency off
- Error details contract matching Expo 422 surface: {type, error, message, reason, alternative}
"""

import json
import logging
import re
import sys
import time
from typing import Any
from urllib.parse import urlparse

import streamlink
from streamlink import Streamlink
from streamlink.exceptions import (
    NoPluginError as SLNoPluginError,
    NoStreamsError as SLNoStreamsError,
    PluginError as SLPluginError,
    StreamlinkError,
)
from streamlink.stream.hls import HLSStream
from streamlink.stream.http import HTTPStream

# Suppress streamlink's noisy plugin-loading logs
logging.getLogger("streamlink.session.plugins").setLevel(logging.CRITICAL)
logging.getLogger("streamlink.plugins").setLevel(logging.CRITICAL)
logging.getLogger("streamlink").setLevel(logging.ERROR)

ALIASES = {'best', 'worst', 'best-unfiltered', 'worst-unfiltered'}

# Codec alignment (§1.3 of plan): widen to core's h264,h265,av1. Keep low-latency off.
FAST_DEFAULTS = {
    'twitch-supported-codecs': 'h264,h265,av1',
    'http-timeout': 10.0,
    'http-retries': 1,
    'hls-playlist-reload-attempts': 2,
}

# ---------------------------------------------------------------------------
# Error taxonomy (verbatim from tukiwatch-core/src/tukiwatch_core/errors.py)
# ---------------------------------------------------------------------------

BROWSER_ERROR_KEYWORDS = (
    "chromium-based web browser",
    "403 client error: forbidden",
    "browser",
    "cloudflare",
)


def is_browser_error(error_msg: str) -> bool:
    """Detect streamlink errors that require browser automation."""
    error_lower = (error_msg or "").lower()
    return any(keyword in error_lower for keyword in BROWSER_ERROR_KEYWORDS)


# Transport failures reach us wrapped in Streamlink's PluginError, so they have
# to be recognised by text. The Kotlin liveness layer uses `error_class` to
# distinguish "no internet / host unreachable" from a plugin verdict; without
# this the offline banner never fires for a real DNS/connection failure.
NETWORK_ERROR_KEYWORDS = (
    "name or service not known",
    "name resolution error",
    "failed to resolve",
    "nodename nor servname",
    "temporary failure in name resolution",
    "max retries exceeded",
    "connection refused",
    "connection reset",
    "connection aborted",
    "connection error",
    "network is unreachable",
    "no route to host",
    "timed out",
    "timeout",
    "eof occurred",
    "remote end closed connection",
    "ssl: certificate_verify_failed",
    "certificate verify failed",
)


def is_network_error(error_msg: str) -> bool:
    """True when the failure is transport-level, not a plugin verdict."""
    error_lower = (error_msg or "").lower()
    return any(keyword in error_lower for keyword in NETWORK_ERROR_KEYWORDS)


def unexpected_error_details(error_msg: str) -> dict[str, Any]:
    """Structured details for the `unexpected` branch of the client contract."""
    return {
        "type": "unexpected",
        "error": "Unexpected error",
        "message": error_msg,
        "reason": "Unexpected failure during stream resolution",
        "alternative": None,
        "error_class": "network" if is_network_error(error_msg) else "unexpected",
    }


def classify_error(url: str, platform: str, error_msg: str) -> dict[str, Any]:
    """Map a streamlink plugin error into the client-visible error taxonomy.

    Surfaces the exact {type, error, message, reason, alternative} shape the Expo
    app's 422 handler and StreamResolution.error_details render.
    """
    platform_name = platform.title() if platform and platform != "unknown" else "Platform"
    if is_browser_error(error_msg):
        return {
            "type": "browser_required",
            "error": f"{platform_name} requires a browser to play.",
            "message": f"Browser automation required for {platform or url}: {url}",
            "reason": "Platform uses anti-bot protection (e.g. Cloudflare / JS challenge)",
            "alternative": "Open this stream in the official app or website.",
            "error_class": "browser_required",
        }
    return {
        "type": "plugin_error",
        "error": f"Failed to extract stream: {error_msg}",
        "message": error_msg,
        "reason": "Plugin reported an error during extraction",
        "alternative": None,
        "error_class": "network" if is_network_error(error_msg) else "plugin_error",
    }


# ---------------------------------------------------------------------------
# Metadata (verbatim from tukiwatch-core/src/tukiwatch_core/metadata.py)
# ---------------------------------------------------------------------------

PLATFORM_COLORS = {
    "twitch": "9146FF",
    "youtube": "FF0000",
    "kick": "53FC18",
    "facebook": "1877F2",
    "instagram": "E4405F",
    "tiktok": "000000",
    "bigo": "FF6B35",
    "dailymotion": "0066DC",
    "vimeo": "1AB7EA",
    "steam": "171A21",
    "bilibili": "FB7299",
    "huya": "FF7F00",
    "picarto": "1DA1F2",
    "trovo": "00D7FF",
    "vk": "4680C2",
    "dlive": "FFD700",
    "goodgame": "00AA00",
    "abematv": "00D4AA",
    "aloula": "FF6B6B",
}

PLATFORM_DOMAINS = {
    "twitch.tv": "twitch",
    "youtube.com": "youtube",
    "youtu.be": "youtube",
    "kick.com": "kick",
    "facebook.com": "facebook",
    "instagram.com": "instagram",
    "tiktok.com": "tiktok",
    "bigo.tv": "bigo",
    "dailymotion.com": "dailymotion",
    "vimeo.com": "vimeo",
    "steamcommunity.com": "steam",
    "bilibili.com": "bilibili",
    "huya.com": "huya",
    "picarto.tv": "picarto",
    "trovo.live": "trovo",
    "vk.com": "vk",
    "dlive.tv": "dlive",
    "goodgame.ru": "goodgame",
    "abema.tv": "abematv",
    "aloula.sa": "aloula",
}


def extract_platform_from_url(url: str) -> str:
    """Extract the platform name from a URL."""
    try:
        domain = urlparse(url).netloc.lower().removeprefix("www.")
    except Exception:
        return "unknown"

    for suffix, platform in PLATFORM_DOMAINS.items():
        if suffix in domain:
            return platform

    try:
        name = domain.split(".")[0]
        return name if name else "unknown"
    except Exception:
        return "unknown"


def parse_url_metadata(url: str) -> dict[str, str]:
    """Parse platform, a preliminary username/ID, and URL type from a URL.

    Mirrors tukiwatch-core.metadata.parse_url_metadata; the KMP side has no
    data class for it yet (Add-screen preview resolves the URL instead).
    """
    if (
        not isinstance(url, str)
        or not url.strip()
        or not url.lower().startswith(("http://", "https://"))
    ):
        return {
            "platform": "Unknown",
            "username": "unknown_stream",
            "url_type": "parse_error",
        }

    try:
        parsed_uri = urlparse(url)
        netloc = parsed_uri.netloc.replace("www.", "")
        path = parsed_uri.path
    except ValueError:
        return {
            "platform": "Unknown",
            "username": "unknown_stream",
            "url_type": "parse_error",
        }

    # Twitch channel
    if "twitch.tv" in netloc:
        m = re.match(r"/([a-zA-Z0-9_]{4,25})/?$", path)
        if m:
            return {"platform": "Twitch", "username": m.group(1), "url_type": "channel"}
        return {"platform": "Twitch", "username": "unknown_user", "url_type": "parse_error"}

    # YouTube channel / video
    if "youtube.com" in netloc or "youtu.be" in netloc:
        channel_match = re.match(
            r"/(?:@([a-zA-Z0-9_.-]+)|c/([a-zA-Z0-9_.-]+)|channel/([a-zA-Z0-9_-]+)|user/([a-zA-Z0-9_.-]+))/?",
            path,
        )
        if channel_match:
            username = next(
                (g for g in channel_match.groups() if g is not None), "unknown_channel"
            )
            return {"platform": "YouTube", "username": username, "url_type": "channel"}
        video_id_match = re.search(r"(?:/watch\?v=|youtu\.be/)([a-zA-Z0-9_-]{11})", url)
        if video_id_match:
            return {
                "platform": "YouTube",
                "username": video_id_match.group(1),
                "url_type": "video",
            }
        return {
            "platform": "YouTube",
            "username": "unknown_youtube_url",
            "url_type": "parse_error",
        }

    # Kick channel
    if "kick.com" in netloc:
        m = re.match(r"/([a-zA-Z0-9_]+)/?$", path)
        if m:
            return {"platform": "Kick", "username": m.group(1), "url_type": "channel"}
        return {"platform": "Kick", "username": "unknown_user", "url_type": "parse_error"}

    # TikTok channel / live / video
    if "tiktok.com" in netloc:
        m = re.match(r"/@([a-zA-Z0-9_.-]+)(?:/(?:live|video/\d+))?/?$", path)
        if m:
            return {"platform": "TikTok", "username": m.group(1), "url_type": "channel"}
        return {"platform": "TikTok", "username": "unknown_user", "url_type": "parse_error"}

    # Generic fallback
    try:
        domain_parts = netloc.split(".")
        platform_name = domain_parts[-2] if len(domain_parts) > 1 else domain_parts[0]
    except IndexError:
        platform_name = "Unknown"

    path_parts = [part.strip() for part in path.split("/") if part.strip()]
    if len(path_parts) > 1 and path_parts[-1].lower() in {"live", "watch", "video", "videos", "stream"}:
        path_parts.pop()
    username_guess = path_parts[-1].lstrip("@") if path_parts else netloc

    return {
        "platform": platform_name,
        "username": username_guess,
        "url_type": "generic_fallback",
    }


def generate_fallback_thumbnail(platform: str, author: str) -> str:
    """Generate a fallback thumbnail URL based on platform brand colors."""
    author = author or "default"
    color = PLATFORM_COLORS.get((platform or "").lower(), "6B7280")
    text_color = "FFFFFF" if (platform or "").lower() != "kick" else "000000"
    return (
        f"https://ui-avatars.com/api/?name={author}&size=300"
        f"&background={color}&color={text_color}&format=png"
    )


def get_stream_types_from_streams(streams: dict) -> list[str]:
    """Extract the set of available stream types (HLS/HTTP/DASH/...) from streams."""
    stream_types = set()
    for stream in streams.values():
        stream_type = type(stream).__name__.replace("Stream", "").lower()
        if stream_type == "hls":
            stream_types.add("HLS")
        elif stream_type == "http":
            stream_types.add("HTTP")
        elif stream_type == "dash":
            stream_types.add("DASH")
        else:
            stream_types.add(stream_type.upper())
    return sorted(stream_types)


_COMMON_PREFIX_RE = re.compile(
    r"^\(?(LIVE|EN DIRECT|ОНЛАЙН|생방송|ライブ)[\]:]*\s*", re.IGNORECASE
)

_SKIP_FIRST_WORDS = {
    "A", "AN", "BE", "IN", "IS", "IT", "OF", "ON", "OR", "SO", "TO", "EL", "LA", "DE",
}


def _clean_common_prefixes(text: str) -> str:
    return _COMMON_PREFIX_RE.sub("", text).strip()


def extract_category_from_json(
    metadata_json: Any, platform: str, url_type: str = "unknown"
) -> str:
    """Extract a category/keywords string from parsed streamlink JSON metadata."""
    if not metadata_json or "metadata" not in metadata_json:
        return "N/A"
    meta = metadata_json.get("metadata", {})
    title = meta.get("title", "")

    platform_key = (platform or "").lower()

    if platform_key == "twitch":
        return str(meta.get("game") or (title.split(" ")[0] if title else "N/A"))

    if platform_key == "youtube":
        clean_title = _clean_common_prefixes(title)
        words = clean_title.split()
        if (
            len(words) > 1
            and len(words[0]) <= 2
            and words[0].upper() not in _SKIP_FIRST_WORDS
        ):
            return " ".join(words[1:4])
        return " ".join(words[:3]) if words else "N/A"

    if platform_key == "kick":
        if ":" in title:
            return title.split(":")[0].strip()
        return title.split(" ")[0] if title else "N/A"

    if platform_key in {"tiktok", "douyin", "bigo live"}:
        return _clean_common_prefixes(title) if title else "N/A"

    if platform_key == "bilibili":
        category = meta.get("game_name", meta.get("category"))
        if category:
            return str(category)
        if " - " in title:
            return _clean_common_prefixes(title).split(" - ")[0]
        return title.split(" ")[0] if title else "N/A"

    return "N/A"


def extract_category_keywords(
    metadata_result: tuple[bool, str], platform: str, url_type: str = "unknown"
) -> str:
    """Extract category/keywords from streamlink JSON metadata (string form)."""
    success, json_data = metadata_result
    if not success:
        return "N/A"
    try:
        metadata_json = json.loads(json_data)
    except (json.JSONDecodeError, TypeError):
        return "N/A"
    return extract_category_from_json(metadata_json, platform, url_type)


_VIEWER_KEYS = ("viewers", "viewer_count", "online")


def extract_viewer_count(meta: dict[str, Any]) -> int | None:
    """Extract a viewer count from metadata, returning None if unknown."""
    if not isinstance(meta, dict):
        return None
    for key in _VIEWER_KEYS:
        if key in meta:
            try:
                value = int(meta[key])
                if value >= 0:
                    return value
            except (TypeError, ValueError):
                continue
    return None


def normalize_stream_metadata(
    meta: dict[str, Any], platform: str, author_fallback: str = ""
) -> dict[str, Any]:
    """Build normalized metadata from a plugin's raw metadata dict."""
    meta = meta or {}
    author = meta.get("author") or author_fallback or "Unknown"
    title = meta.get("title") or "Live Stream"
    category = meta.get("category") or meta.get("game") or ""
    return {
        "title": title,
        "author": author,
        "category": category,
        "stream_id": str(meta.get("id") or ""),
        "platform": platform,
        "thumbnail": generate_fallback_thumbnail(platform, author),
        "viewer_count": extract_viewer_count(meta),
    }


# ---------------------------------------------------------------------------
# Session management (singleton on Android, pool size 1)
# ---------------------------------------------------------------------------

_SESSION = None
_SESSION_OPTS_KEY = None


def version() -> str:
    return streamlink.__version__


def _safe_set(session, key, value, skipped):
    try:
        session.set_option(key, value)
    except Exception:
        skipped.append(str(key))


def get_session(options_dict=None):
    global _SESSION, _SESSION_OPTS_KEY
    options_dict = options_dict or {}
    key = json.dumps(options_dict, sort_keys=True, default=str)
    if _SESSION is not None and key == _SESSION_OPTS_KEY:
        return _SESSION, True
    session = Streamlink()
    session.http.headers["User-Agent"] = (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/128.0.0.0 Safari/537.36"
    )
    skipped = []
    for k, v in FAST_DEFAULTS.items():
        _safe_set(session, k, v, skipped)
    for k, v in options_dict.items():
        _safe_set(session, k, v, skipped)
    _SESSION = session
    _SESSION_OPTS_KEY = key
    get_session.last_skipped = skipped
    return session, False


def warmup(url=None) -> str:
    t0 = time.perf_counter()
    # Never replace an already-configured session: warmup is called at process
    # start and must not wipe options a later resolve installed.
    if _SESSION is not None:
        session, reused = _SESSION, True
    else:
        session, reused = get_session({})
    timings = {'session_ms': (time.perf_counter() - t0) * 1000.0, 'reused': reused}
    if url:
        try:
            t1 = time.perf_counter()
            session.resolve_url(url)
            timings['resolve_ms'] = (time.perf_counter() - t1) * 1000.0
        except Exception as exc:
            timings['resolve_error'] = '%s: %s' % (type(exc).__name__, exc)
    timings['version'] = streamlink.__version__
    return json.dumps(timings)


def _headers(session, stream):
    headers = dict(session.http.headers)
    args = getattr(stream, 'args', None) or {}
    headers.update(args.get('headers') or {})
    try:
        cookies = {c.name: c.value for c in session.http.cookies}
    except Exception:
        cookies = {}
    if cookies:
        headers['Cookie'] = '; '.join(k + '=' + v for k, v in cookies.items())
    return {str(k): str(v) for k, v in headers.items()}


# ---------------------------------------------------------------------------
# Core resolve entry points (called from Kotlin bridge)
# ---------------------------------------------------------------------------


def resolve_details(url: str, options_json: str = '{}', wanted: str = 'all') -> str:
    """Full stream resolution with metadata, qualities and error details.

    Implements the StreamResolution domain model contract from tukiwatch-core.
    Returns a JSON string matching EngineResult on the Kotlin side.
    """
    t_total = time.perf_counter()
    platform = extract_platform_from_url(url)
    parsed_meta = parse_url_metadata(url)
    default_author = parsed_meta.get("username")

    try:
        options_dict = json.loads(options_json) if options_json else {}
    except Exception:
        options_dict = {}

    t0 = time.perf_counter()
    try:
        session, reused = get_session(options_dict)
    except Exception as exc:
        return json.dumps({
            'status': 'error',
            'error': 'unexpected',
            'message': f"session: {type(exc).__name__}: {exc}",
            'original_url': url,
            'platform': platform,
        })
    session_ms = (time.perf_counter() - t0) * 1000.0

    t1 = time.perf_counter()
    plugin_name = None
    try:
        plugin_name, plugin_class, resolved_url = session.resolve_url(url)
        plugin_instance = plugin_class(session, resolved_url)
        streams = plugin_instance.streams()
    except SLNoPluginError:
        return json.dumps({
            'status': 'error',
            'error': 'no_plugin',
            'message': f"No plugin available for URL: {url}",
            'original_url': url,
            'platform': platform,
            'error_details': {
                'type': 'no_plugin',
                'error': 'No plugin available for this URL',
                'message': f"No plugin available for URL: {url}",
                'reason': 'Streamlink does not have a plugin for this site',
                'alternative': 'Try opening in official app',
                'error_class': 'no_plugin',
            },
        })
    except SLNoStreamsError:
        # tukiwatch-core maps "plugin resolved, but no streams" to offline, not
        # to a plugin failure (errors.py: NoStreamsError = "offline").
        author_fallback = (
            plugin_name
            if (plugin_name and plugin_name.lower() != platform.lower())
            else (default_author or plugin_name or platform)
        )
        return json.dumps({
            'status': 'offline',
            'error': None,
            'message': 'No streams available',
            'original_url': url,
            'platform': platform,
            'author': author_fallback,
            'title': 'Offline',
            'thumbnail': generate_fallback_thumbnail(platform, author_fallback),
            'streams': [],
            '_timings': {
                'session_ms': round(session_ms, 1),
                'streams_ms': round((time.perf_counter() - t1) * 1000.0, 1),
                'build_ms': 0.0,
                'total_ms': round((time.perf_counter() - t_total) * 1000.0, 1),
                'session_reused': reused,
                'count': 0,
            },
        })
    except (SLPluginError, StreamlinkError) as exc:
        error_msg = str(exc)
        classified = classify_error(url, platform, error_msg)
        return json.dumps({
            'status': 'error',
            'error': classified['type'],
            'message': error_msg,
            'original_url': url,
            'platform': platform,
            'error_details': classified,
        })
    except Exception as exc:
        error_msg = f"{type(exc).__name__}: {exc}"
        return json.dumps({
            'status': 'error',
            'error': 'unexpected',
            'message': error_msg,
            'original_url': url,
            'platform': platform,
            'error_details': unexpected_error_details(error_msg),
        })
    streams_ms = (time.perf_counter() - t1) * 1000.0

    if not streams:
        author_fallback = (
            plugin_name
            if (plugin_name and plugin_name.lower() != platform.lower())
            else (default_author or plugin_name or platform)
        )
        return json.dumps({
            'status': 'offline',
            'original_url': url,
            'platform': platform,
            'author': author_fallback,
            'title': 'Offline',
            'thumbnail': generate_fallback_thumbnail(platform, author_fallback),
            'streams': [],
            '_timings': {
                'session_ms': round(session_ms, 1),
                'streams_ms': round(streams_ms, 1),
                'build_ms': 0.0,
                'total_ms': round((time.perf_counter() - t_total) * 1000.0, 1),
                'session_reused': reused,
                'count': 0,
            },
        })

    # Metadata extraction via plugin.get_metadata()
    try:
        raw_meta = plugin_instance.get_metadata() or {}
    except Exception:
        raw_meta = {}

    normalized = normalize_stream_metadata(raw_meta, platform, author_fallback=default_author or plugin_name)
    stream_types = get_stream_types_from_streams(streams)

    t2 = time.perf_counter()
    materialize_all = (wanted == 'all')
    keep = None
    if not materialize_all:
        keep = {wanted, 'best', 'worst'} & set(streams.keys())

    items = []
    # tukiwatch-core: best_quality = streams["best"].url. The synonym points at
    # the same stream object as the best real quality, so this stays in sync.
    best_quality_url = getattr(streams.get('best'), 'url', None)
    for name, stream in streams.items():
        direct = isinstance(stream, (HLSStream, HTTPStream))
        try:
            stream_url = stream.url if direct else None
        except Exception:
            stream_url = None
            direct = False

        if keep is not None and name not in keep:
            continue

        items.append({
            'quality': name,
            'alias': name in ALIASES,
            'kind': type(stream).__name__,
            'playable_direct': direct and stream_url is not None,
            'url': stream_url if direct else None,
            'headers': _headers(session, stream) if (direct and stream_url) else {},
        })
    build_ms = (time.perf_counter() - t2) * 1000.0
    total_ms = (time.perf_counter() - t_total) * 1000.0

    payload = {
        'status': 'online',
        'error': None,
        'message': None,
        'original_url': url,
        'platform': platform,
        'title': normalized['title'],
        'author': normalized['author'],
        'thumbnail': normalized['thumbnail'],
        'category': normalized['category'],
        'stream_id': normalized['stream_id'],
        'viewer_count': normalized['viewer_count'],
        'stream_types': stream_types,
        'best_quality': best_quality_url,
        'streams': items,
        'wanted': wanted,
        'quality_index': sorted(streams.keys()) if not materialize_all else None,
        '_timings': {
            'session_ms': round(session_ms, 1),
            'streams_ms': round(streams_ms, 1),
            'build_ms': round(build_ms, 1),
            'total_ms': round(total_ms, 1),
            'session_reused': reused,
            'count': len(items),
        },
    }
    skipped = getattr(get_session, 'last_skipped', None)
    if skipped:
        payload['_timings']['skipped_options'] = skipped
    return json.dumps(payload)


def check_status(url: str, options_json: str = '{}') -> str:
    """Cheap liveness check (StreamStatus domain model parity).

    Used for non-Twitch staggered status checks (§3 of plan). Returns a lightweight
    JSON payload: status (online/offline/error), title, author, thumbnail, category,
    stream_id, platform, error, error_details.
    """
    platform = extract_platform_from_url(url)
    parsed_meta = parse_url_metadata(url)
    default_author = parsed_meta.get("username")
    try:
        options_dict = json.loads(options_json) if options_json else {}
    except Exception:
        options_dict = {}
    try:
        session, _ = get_session(options_dict)
        plugin_name, plugin_class, resolved_url = session.resolve_url(url)
        plugin_instance = plugin_class(session, resolved_url)
        streams = plugin_instance.streams()

        if not streams:
            author_fallback = (
                plugin_name
                if (plugin_name and plugin_name.lower() != platform.lower())
                else (default_author or plugin_name or platform)
            )
            return json.dumps({
                'url': url,
                'status': 'offline',
                'platform': platform,
                'title': 'Offline',
                'author': author_fallback,
                'thumbnail': generate_fallback_thumbnail(platform, author_fallback),
            })

        raw_meta = plugin_instance.get_metadata() or {}
        normalized = normalize_stream_metadata(raw_meta, platform, author_fallback=default_author or plugin_name)
        return json.dumps({
            'url': url,
            'status': 'online',
            'platform': platform,
            'title': normalized['title'],
            'author': normalized['author'],
            'thumbnail': normalized['thumbnail'],
            'category': normalized['category'],
            'stream_id': normalized['stream_id'],
            'viewer_count': normalized['viewer_count'],
        })
    except SLNoPluginError:
        return json.dumps({
            'url': url,
            'status': 'error',
            'platform': platform,
            'error': 'No plugin available for this URL',
            'error_details': {
                'type': 'no_plugin',
                'error': 'No plugin available for this URL',
                'message': f"No plugin available for URL: {url}",
                'reason': 'Streamlink does not support this domain',
                'alternative': 'Try opening in official app',
                'error_class': 'no_plugin',
            },
        })
    except SLNoStreamsError:
        # Offline is not an error: no `error` string, and the same fallback
        # title/author/thumbnail the resolve path returns so My List rows read
        # "Offline" instead of surfacing an internal sentinel.
        author_fallback = (
            plugin_name
            if (plugin_name and plugin_name.lower() != platform.lower())
            else (default_author or plugin_name or platform)
        )
        return json.dumps({
            'url': url,
            'status': 'offline',
            'platform': platform,
            'title': 'Offline',
            'author': author_fallback,
            'thumbnail': generate_fallback_thumbnail(platform, author_fallback),
            'error': None,
        })
    except (SLPluginError, StreamlinkError) as exc:
        classified = classify_error(url, platform, str(exc))
        return json.dumps({
            'url': url,
            'status': 'error',
            'platform': platform,
            'error': classified['type'],
            'error_details': classified,
        })
    except Exception as exc:
        error_msg = f"{type(exc).__name__}: {exc}"
        return json.dumps({
            'url': url,
            'status': 'error',
            'platform': platform,
            'error': error_msg,
            'error_details': unexpected_error_details(error_msg),
        })


# Backward-compatible alias for prototype callers
def list_streams(url: str, options_json: str = '{}', wanted: str = 'all') -> str:
    return resolve_details(url, options_json, wanted)


if __name__ == '__main__':
    # Two supported CLIs:
    #   stream_engine.py <entrypoint> [args...]   (SubprocessStreamEngine)
    #   stream_engine.py <url> [wanted]           (legacy manual use)
    _ENTRYPOINTS = {'resolve_details', 'check_status', 'list_streams', 'warmup', 'version'}
    _argv = sys.argv[1:]
    if _argv and _argv[0] in _ENTRYPOINTS:
        print(globals()[_argv[0]](*_argv[1:]))
    else:
        _target = _argv[0] if _argv else 'hls://https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8'
        _wanted = _argv[1] if len(_argv) > 1 else 'all'
        print(resolve_details(_target, '{}', _wanted))
