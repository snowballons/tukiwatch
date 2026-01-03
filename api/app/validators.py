from urllib.parse import urlparse
from fastapi import HTTPException


def validate_url(url: str) -> str:
    """Validate and normalize URL"""
    if not url or not url.strip():
        raise HTTPException(status_code=400, detail="URL is required")

    url = url.strip()

    # Add protocol if missing
    if not url.startswith(("http://", "https://")):
        url = "https://" + url

    # Parse URL
    try:
        parsed = urlparse(url)
        if not parsed.netloc:
            raise HTTPException(status_code=400, detail="Invalid URL format")
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid URL format")

    # Check for supported domains (top 20 streaming platforms)
    supported_domains = [
        "twitch.tv",
        "youtube.com",
        "youtu.be",
        "kick.com",
        "facebook.com",
        "instagram.com",
        "tiktok.com",
        "bigo.tv",
        "dailymotion.com",
        "vimeo.com",
        "steamcommunity.com",
        "bilibili.com",
        "huya.com",
        "picarto.tv",
        "trovo.live",
        "ustream.tv",
        "vk.com",
        "dlive.tv",
        "goodgame.ru",
        "abema.tv",
        "aloula.sa",
    ]

    domain = parsed.netloc.lower()
    # Remove www. prefix
    if domain.startswith("www."):
        domain = domain[4:]

    if not any(domain.endswith(supported) for supported in supported_domains):
        raise HTTPException(
            status_code=422,
            detail=f"Unsupported domain: {domain}. Supported: {', '.join(supported_domains)}",
        )

    return url


def validate_batch_request(urls: list) -> list:
    """Validate batch request URLs"""
    if not urls:
        raise HTTPException(status_code=400, detail="URLs list cannot be empty")

    if len(urls) > 20:
        raise HTTPException(status_code=400, detail="Maximum 20 URLs per batch")

    validated_urls = []
    for i, url in enumerate(urls):
        try:
            validated_urls.append(validate_url(url))
        except HTTPException:
            # For batch requests, we'll be more lenient and skip invalid URLs
            # but still validate the format
            if not url or not url.strip():
                continue
            validated_urls.append(url.strip())

    if not validated_urls:
        raise HTTPException(status_code=400, detail="No valid URLs provided")

    return validated_urls
