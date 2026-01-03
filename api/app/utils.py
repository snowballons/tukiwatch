from urllib.parse import urlparse


def extract_platform_from_url(url: str) -> str:
    """Extract platform name from URL"""
    try:
        domain = urlparse(url).netloc.lower()
        if domain.startswith("www."):
            domain = domain[4:]

        if "twitch.tv" in domain:
            return "twitch"
        elif "youtube.com" in domain or "youtu.be" in domain:
            return "youtube"
        elif "kick.com" in domain:
            return "kick"
        elif "facebook.com" in domain:
            return "facebook"
        elif "instagram.com" in domain:
            return "instagram"
        elif "tiktok.com" in domain:
            return "tiktok"
        elif "bigo.tv" in domain:
            return "bigo"
        elif "dailymotion.com" in domain:
            return "dailymotion"
        elif "vimeo.com" in domain:
            return "vimeo"
        elif "steamcommunity.com" in domain:
            return "steam"
        elif "bilibili.com" in domain:
            return "bilibili"
        elif "huya.com" in domain:
            return "huya"
        elif "picarto.tv" in domain:
            return "picarto"
        elif "trovo.live" in domain:
            return "trovo"
        elif "ustream.tv" in domain:
            return "ustreamtv"
        elif "vk.com" in domain:
            return "vk"
        elif "dlive.tv" in domain:
            return "dlive"
        elif "goodgame.ru" in domain:
            return "goodgame"
        elif "abema.tv" in domain:
            return "abematv"
        elif "aloula.sa" in domain:
            return "aloula"
        else:
            return domain.split(".")[0]
    except Exception:
        return "unknown"


def generate_fallback_thumbnail(platform: str, author: str) -> str:
    """Generate fallback thumbnail URLs based on platform"""
    author = author or "default"

    # Platform-specific colors (using brand colors where possible)
    platform_colors = {
        "twitch": "9146FF",  # Purple
        "youtube": "FF0000",  # Red
        "kick": "53FC18",  # Green
        "facebook": "1877F2",  # Blue
        "instagram": "E4405F",  # Pink
        "tiktok": "000000",  # Black
        "bigo": "FF6B35",  # Orange
        "dailymotion": "0066DC",  # Blue
        "vimeo": "1AB7EA",  # Light Blue
        "steam": "171A21",  # Dark Gray
        "bilibili": "FB7299",  # Pink
        "huya": "FF7F00",  # Orange
        "picarto": "1DA1F2",  # Blue
        "trovo": "00D7FF",  # Cyan
        "ustreamtv": "3388CC",  # Blue
        "vk": "4680C2",  # Blue
        "dlive": "FFD700",  # Gold
        "goodgame": "00AA00",  # Green
        "abematv": "00D4AA",  # Teal
        "aloula": "FF6B6B",  # Red
    }

    color = platform_colors.get(platform, "6B7280")  # Default gray
    text_color = (
        "FFFFFF" if platform != "kick" else "000000"
    )  # Black text for kick (light green bg)

    return f"https://ui-avatars.com/api/?name={author}&size=300&background={color}&color={text_color}&format=png"


def get_stream_types_from_streams(streams: dict) -> list:
    """Extract available stream types from streamlink streams"""
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
    return sorted(list(stream_types))
