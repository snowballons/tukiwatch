"""License activation/session endpoints (Polar-backed).

The Polar license key is exchanged exactly once for a short-lived TukiWatch
session (``tw_sess_*``). The key itself is never used as an API credential
and never echoed back. See polar-plan1.md §5.3.
"""

from typing import Any

from fastapi import APIRouter, Header, HTTPException, Request
from pydantic import BaseModel, Field

from app.polar_service import polar_service
from app.session_service import session_service
from config import config

router = APIRouter(prefix="/api/license", tags=["license"])


class ActivateRequest(BaseModel):
    license_key: str = Field(min_length=1, max_length=256)
    label: str | None = Field(default=None, max_length=128)
    conditions: dict[str, Any] | None = None


class ActivateResponse(BaseModel):
    session_token: str
    tier: str
    expires_in: int  # seconds until session expires


class ValidateResponse(BaseModel):
    valid: bool
    tier: str | None = None
    license_id: str | None = None
    expires_at: str | None = None  # session expiration


class DeactivateRequest(BaseModel):
    session_token: str = Field(min_length=1, max_length=128)


def _license_error() -> HTTPException:
    return HTTPException(
        status_code=401,
        detail={"error": "Invalid license key", "type": "license_error"},
    )


@router.post("/activate", response_model=ActivateResponse)
async def activate_license(request: ActivateRequest):
    """Exchange a Polar license key for a TukiWatch session token."""
    license_data = await polar_service.validate_key(request.license_key.strip())
    if not license_data:
        raise _license_error()

    # Device-slot enforcement: when the app sends a device label and the
    # benefit has an activation limit that is already full, pin the key to
    # this device (Polar enforces the cap).
    max_activations = license_data.get("max_activations")
    current_activations = license_data.get("current_activations") or 0
    if (
        request.label
        and max_activations is not None
        and current_activations >= max_activations
    ):
        activation = await polar_service.activate_key(
            request.license_key.strip(),
            request.label,
            conditions=request.conditions,
        )
        if not activation:
            raise HTTPException(
                status_code=403,
                detail={
                    "error": "Device limit reached. Deactivate an old device in "
                    "the Polar customer portal and try again.",
                    "type": "activation_limit_error",
                },
            )

    session_token = session_service.create_session(
        license_id=license_data.get("key_id") or "unknown",
        tier="supporter",
    )
    return ActivateResponse(
        session_token=session_token,
        tier="supporter",
        expires_in=config.SESSION_TTL_HOURS * 3600,
    )


@router.post("/validate", response_model=ValidateResponse)
async def validate_session(request: Request, authorization: str | None = Header(None)):
    """Validate a session token (``Authorization: Bearer <token>``).

    Local session check only — no Polar call per request. Always returns 200
    with ``valid`` true/false so middleware stays simple.
    """
    _ = request  # reserved for future per-request context
    if not authorization or not authorization.startswith("Bearer "):
        return ValidateResponse(valid=False)
    session_data = session_service.validate_session(authorization.split(" ", 1)[1])
    if not session_data:
        return ValidateResponse(valid=False)
    return ValidateResponse(
        valid=True,
        tier=session_data["tier"],
        license_id=session_data["license_id"],
        expires_at=session_data["expires_at"],
    )


@router.post("/deactivate")
async def deactivate_license(request: DeactivateRequest):
    """Revoke a session immediately (logout)."""
    success = session_service.revoke_session(request.session_token)
    if not success:
        raise HTTPException(
            status_code=400,
            detail={
                "error": "Invalid or already revoked session",
                "type": "session_error",
            },
        )
    return {"status": "revoked"}
