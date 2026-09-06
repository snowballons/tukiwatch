"""Polar webhook handler (sync acceleration, not source of truth).

Sessions expire naturally within 24h; webhooks only make revocation faster.
Rules (see polar-plan1.md §5.5):
- Revoke app access ONLY on ``benefit_grant.revoked`` / ``subscription.revoked``
  (plus local session expiry). ``subscription.canceled`` alone is a scheduled
  cancel — the customer keeps access until period end.
- ``order.refunded`` on a subscription does NOT revoke (refund returns money
  only); wait for ``subscription.revoked``.

Signature: Standard Webhooks (``webhook-id`` / ``webhook-timestamp`` /
``webhook-signature`` headers) with legacy Polar-HMAC ``X-Signature`` fallback.
No new dependencies — verified with hmac/hashlib/base64.
"""

import base64
import hashlib
import hmac
import logging
import time
from typing import Any

from fastapi import APIRouter, HTTPException, Request

from app.session_service import session_service
from config import config

logger = logging.getLogger(__name__)

router = APIRouter()

SIGNATURE_TOLERANCE_SECONDS = 300

# Events that end access immediately.
REVOKE_GRANT_EVENTS = {"benefit_grant.revoked"}
REVOKE_SUBSCRIPTION_EVENTS = {"subscription.revoked"}


def _verify_standard_webhooks(
    secret: str, msg_id: str, msg_timestamp: str, signatures: str, raw_body: bytes
) -> bool:
    """Verify Standard Webhooks ``v1,<base64>`` signature(s)."""
    try:
        ts = int(msg_timestamp)
    except (TypeError, ValueError):
        return False
    if abs(time.time() - ts) > SIGNATURE_TOLERANCE_SECONDS:
        logger.warning("Polar webhook timestamp outside tolerance")
        return False
    signed = f"{msg_id}.{msg_timestamp}.".encode() + raw_body
    expected = base64.b64encode(
        hmac.new(secret.encode(), signed, hashlib.sha256).digest()
    ).decode()
    for candidate in signatures.split():
        if "," not in candidate:
            continue
        version, _, signature = candidate.partition(",")
        if version.strip() != "v1":
            continue
        if hmac.compare_digest(signature.strip(), expected):
            return True
    return False


def _verify_legacy_hmac(secret: str, signature: str, raw_body: bytes) -> bool:
    """Verify legacy Polar-HMAC ``X-Signature`` (hex digest of raw body)."""
    expected = hmac.new(secret.encode(), raw_body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(signature.strip(), expected)


def _extract_license_ids(data: dict[str, Any]) -> set[str]:
    """Collect candidate Polar license-key ids from a webhook payload."""
    found: set[str] = set()
    candidates: list[Any] = [
        data.get("license_key_id"),
        data.get("license_key", {}).get("id")
        if isinstance(data.get("license_key"), dict)
        else None,
        data.get("properties", {}).get("license_key_id")
        if isinstance(data.get("properties"), dict)
        else None,
    ]
    for candidate in candidates:
        if candidate:
            found.add(str(candidate))
    return found


def _extract_customer_id(data: dict[str, Any]) -> str | None:
    customer = data.get("customer")
    if isinstance(customer, dict) and customer.get("id"):
        return str(customer["id"])
    if data.get("customer_id"):
        return str(data["customer_id"])
    return None


@router.post("/webhooks/polar")
async def handle_polar_webhook(request: Request):
    """Verify and process Polar webhook events."""
    secret = config.POLAR_WEBHOOK_SECRET
    if not secret:
        logger.error("Polar webhook secret not configured")
        raise HTTPException(status_code=500, detail="Webhook misconfigured")

    raw_body = await request.body()
    headers = request.headers
    msg_id = headers.get("webhook-id")
    msg_timestamp = headers.get("webhook-timestamp")
    signatures = headers.get("webhook-signature")
    legacy_signature = headers.get("x-signature")

    verified = False
    if msg_id and msg_timestamp and signatures:
        verified = _verify_standard_webhooks(
            secret, msg_id, msg_timestamp, signatures, raw_body
        )
    elif legacy_signature:
        verified = _verify_legacy_hmac(secret, legacy_signature, raw_body)

    if not verified:
        logger.warning("Invalid Polar webhook signature")
        raise HTTPException(status_code=401, detail="Invalid signature")

    try:
        event: Any = await request.json()
    except Exception:
        logger.warning("Polar webhook with invalid JSON payload")
        raise HTTPException(status_code=400, detail="Invalid payload") from None
    if not isinstance(event, dict):
        raise HTTPException(status_code=400, detail="Invalid payload") from None

    event_type = str(event.get("type") or "")
    data = event.get("data") or {}
    if not isinstance(data, dict):
        data = {}
    logger.info("Received Polar webhook: %s", event_type or "unknown")

    if event_type in REVOKE_GRANT_EVENTS:
        revoked = 0
        for license_id in _extract_license_ids(data):
            revoked += session_service.revoke_sessions_for_license(license_id)
        customer_id = _extract_customer_id(data)
        if customer_id:
            revoked += session_service.revoke_sessions_for_customer(customer_id)
        logger.info("Grant revocation processed (%s: revoked=%d)", event_type, revoked)
    elif event_type in REVOKE_SUBSCRIPTION_EVENTS:
        customer_id = _extract_customer_id(data)
        revoked = (
            session_service.revoke_sessions_for_customer(customer_id)
            if customer_id
            else 0
        )
        logger.info(
            "Subscription revocation processed (%s: revoked=%d)", event_type, revoked
        )
    # All other events (state_changed, canceled, past_due, cycled, orders,
    # refunds): logged sync signal only. Sessions expire naturally; access is
    # re-checked against Polar on next activation.

    return {"status": "processed"}
