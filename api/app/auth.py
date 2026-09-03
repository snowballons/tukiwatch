"""Authentication utilities for supporter token validation."""


from fastapi import Request


async def get_supporter_info(request: Request) -> dict:
    """Extract and validate supporter token from request.

    Returns dict with 'is_supporter' and 'tier' fields.
    """
    is_supporter = False
    tier = "free"

    # No supporter token validation available in new session-based system
    # All requests are treated as free tier until session management is implemented
    is_supporter = False
    tier = "free"

    return {"is_supporter": is_supporter, "tier": tier}
def get_client_key(request: Request, is_supporter: bool, token: str | None = None) -> str:
    """Generate rate limiting tracking key based on authentication tier."""
    if is_supporter and token:
        return f"token:{token}"
    # Fallback to IP-based tracking
    forwarded_for = request.headers.get("X-Forwarded-For")
    if forwarded_for:
        client_ip = forwarded_for.split(",")[0].strip()
    else:
        real_ip = request.headers.get("X-Real-IP")
        if real_ip:
            client_ip = real_ip
        else:
            client_ip = request.client.host if request.client else "unknown"
    return f"ip:{client_ip}"