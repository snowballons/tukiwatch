"""Authentication utilities for supporter session validation."""

import hashlib

from fastapi import Request

from app.session_service import session_service


def extract_bearer_token(request: Request) -> str | None:
    """Extract a ``tw_sess_*`` token from ``Authorization: Bearer <token>``."""
    authorization = request.headers.get("Authorization")
    if not authorization or not authorization.startswith("Bearer "):
        return None
    token = authorization.split(" ", 1)[1].strip()
    return token or None


async def get_supporter_info(request: Request) -> dict:
    """Extract and validate supporter session from request.

    Returns dict with 'is_supporter' and 'tier' fields.
    """
    is_supporter = False
    tier = "free"

    token = extract_bearer_token(request)
    if token:
        session_data = session_service.validate_session(token)
        if session_data:
            is_supporter = True
            tier = session_data.get("tier", "supporter")

    return {"is_supporter": is_supporter, "tier": tier}


def get_client_key(
    request: Request, is_supporter: bool, token: str | None = None
) -> str:
    """Generate rate limiting tracking key based on authentication tier.

    Supporters are tracked by a truncated hash of the session token — the raw
    token never appears in rate-limit keys, logs, or metrics.
    """
    if is_supporter and token:
        hashed = hashlib.sha256(token.encode()).hexdigest()[:16]
        return f"session:{hashed}"
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
