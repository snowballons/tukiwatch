Now I have the complete picture. Let me write the implementation planNow I have everything. Let me write the implementation plan# Revised Implementation Plan: TukiWatch Supporter System with Lemon Squeezy Licensing

This plan replaces the custom token/UUID system with Lemon Squeezy's native licensing system. It implements a clean separation: Lemon Squeezy handles payment/subscription/license entitlement; TukiWatch API validates licenses and creates short-lived session tokens for API authentication.

Phase 0: Cleanup & Prerequisites

Remove files and code related to the old token system:

1. Delete these files:
   - api/app/tokens.py
   - api/app/webhooks.py (doesn't exist yet, but remove placeholder if created)
   - api/app/routers/supporters.py
   - api/tests/test_tokens.py
   - Remove supporterToken from app/src/lib/backendConfig.ts (will be replaced by session token)
   - Remove SecureStore import/usage from app/src/screens/Settings/BackendSettings.tsx (will be replaced by session handling)
   - Remove SUPPORTER_TOKENS and related config from api/config.py

2. Update dependencies:
   bash
Backend
   uv add lemonsqueezy  # Official Python client or use requests
Keep redis, fastapi, etc.
   

3. Environment cleanup:
   Remove from .env and .env.example:
   - SUPPORTER_TOKENS
   - SENDGRID_API_KEY (Lemon Squeezy sends the license email)
   - SUPPORTER_EMAIL_TEMPLATE
   - LEMON_SQUEEZY_WEBHOOK_SECRET (keep - still needed for webhooks)
   Add:
   - LEMON_SQUEEZY_API_KEY (for License API calls)
   - LEMON_SQUEEZY_STORE_ID (your TukiWatch store ID)
   - LEMON_SQUEEZY_PRODUCT_ID (TukiWatch Supporter product ID)
   - LEMON_SQUEEZY_VARIANT_ID ($5/month variant ID)

Phase 1: Lemon Squeezy Product Setup (Manual, done before coding)

1. Create Lemon Squeezy account.
2. Create digital product: "TukiWatch Supporter"
3. Create variant: Supporter at $5/month
4. Enable License Keys on the product.
5. Copy Product ID and Variant ID from the dashboard into .env.
6. Generate an API key (Settings → API Keys) → set LEMON_SQUEEZY_API_KEY.
7. Set webhook endpoint in dashboard: POST https://yourdomain.com/webhooks/lemonsqueezy
8. Subscribe to events:
   - order_created
   - subscription_payment_success
   - subscription_updated
   - subscription_cancelled
   - subscription_expired
   - license_key_created
   - license_key_updated
   - license_key_expired
9. Set webhook secret → LEMON_SQUEEZY_WEBHOOK_SECRET.

Phase 2: Core License Validation & Session System

2.1 License Service (api/app/license_service.py)

python
"""Service for interacting with Lemon Squeezy License API."""
import os
import hmac
import hashlib
from typing import Optional, Dict, Any
import httpx
from config import config
import logging

logger = logging.getLogger(name)

LEMONSQUEEZY_API_URL = "https://api.lemonsqueezy.com/v1"
LEMONSQUEEZY_API_KEY = config.LEMON_SQUEEZY_API_KEY
STORE_ID = config.LEMON_SQUEEZY_STORE_ID
PRODUCT_ID = config.LEMON_SQUEEZY_PRODUCT_ID
VARIANT_ID = config.LEMON_SQUEEZY_VARIANT_ID

class LicenseService:
    def init(self):
        self._client = httpx.AsyncClient(
            base_url=LEMONSQUEEZY_API_URL,
            headers={
                "Accept": "application/vnd.api+json",
                "Content-Type": "application/vnd.api+json",
                "Authorization": f"Bearer {LEMONSQUEEZY_API_KEY}",
            },
            timeout=30.0,
        )

    async def _request(self, method: str, endpoint: str, **kwargs) -> Dict[str, Any]:
        response = await self._client.request(method, endpoint, **kwargs)
        response.raise_for_status()
        return response.json()

    async def validate_license(self, license_key: str) -> Optional[Dict[str, Any]]:
        """
        Validate a license key with Lemon Squeezy.
        Returns license data if valid and belongs to our product/variant.
        """
        try:
            data = await self._request(
                "GET",
                f"/licenses/{license_key}",
            )
        except httpx.HTTPStatusError as exc:
            if exc.response.status_code == 404:
                return None  # License not found/invalid
            logger.error(f"Lemon Squeezy API error: {exc}")
            return None
        except Exception as exc:
            logger.error(f"Unexpected error validating license: {exc}")
            return None

        # Extract relevant data
        license_data = data.get("data", {})
        attributes = license_data.get("attributes", {})
        relationships = license_data.get("relationships", {})

        # Check status
        status = attributes.get("status")
        if status not in ("active", "inactive"):  # inactive = within trial/grace period?
            return None

        # Verify product
        product_rel = relationships.get("product", {}).get("data", {})
        if product_rel.get("id") != str(PRODUCT_ID):
            logger.warning(f"License product mismatch: {product_rel.get('id')} vs {PRODUCT_ID}")
            return None

        # Verify variant (if present)
        variant_rel = relationships.get("variant", {}).get("data", {})
        if variant_rel and variant_rel.get("id") != str(VARIANT_ID):
            logger.warning(f"License variant mismatch: {variant_rel.get('id')} vs {VARIANT_ID}")
            return None

        return {
            "license_id": license_data.get("id"),
            "license_key": license_key,
            "status": status,
            "renews_at": attributes.get("renews_at"),
            "expires_at": attributes.get("expires_at"),
            "trial_ends_at": attributes.get("trial_ends_at"),
            # Store relationship IDs for audit if needed
            "product_id": product_rel.get("id"),
            "variant_id": variant_rel.get("id"),
        }

Global instance
license_service = LicenseService()


2.2 Session Service (api/app/session_service.py)

python
"""Service for creating and validating TukiWatch session tokens."""
import uuid
import hashlib
from datetime import datetime, timedelta
from typing import Optional, Dict
import redis
from config import config

class SessionService:
    TOKEN_PREFIX = "tw_sess_"
    SESSION_TTL_HOURS = 24  # Short-lived sessions
    AUDIT_TTL_DAYS = 7

    def init(self):
        self._redis = None
        if config.REDIS_URL:
            self._redis = redis.Redis.from_url(config.REDIS_URL, decode_responses=True)

    def _get_redis(self) -> Optional[redis.Redis]:
        return self._redis

    def _hash_token(self, token: str) -> str:
        return hashlib.sha256(token.encode()).hexdigest()

    def create_session(self, license_id: str, tier: str = "supporter") -> str:
        """Create a new session token linked to a license."""
        session_id = f"{self.TOKEN_PREFIX}{uuid.uuid4().hex[:16]}"
        issued_at = datetime.utcnow().isoformat()
        expires_at = (datetime.utcnow() + timedelta(hours=self.SESSION_TTL_HOURS)).isoformat()

        session_data = {
            "id": session_id,
            "license_id": license_id,
            "tier": tier,
            "issued_at": issued_at,
            "expires_at": expires_at,
            "revoked": "False",
        }

        r = self._get_redis()
        if r:
            r.hset(session_id, mapping=session_data)
            r.expire(session_id, timedelta(hours=self.SESSION_TTL_HOURS))

            # Audit log (hashed session ID)
            audit_key = f"session_audit:{self._hash_token(session_id)}"
            audit_data = {
                **session_data,
                "session_hash": self._hash_token(session_id),
                "created_at": datetime.utcnow().isoformat(),
                "last_used": "",
                "usage_count": "0",
            }
            r.hset(audit_key, mapping=audit_data)
            r.expire(audit_key, timedelta(days=self.AUDIT_TTL_DAYS))

        return session_id

    def validate_session(self, session_token: str) -> Optional[Dict[str, Any]]:
        """Validate session token and return metadata."""
        if not session_token or not session_token.startswith(self.TOKEN_PREFIX):
            return None

        r = self._get_redis()
        if not r:
            return None  # Fallback: treat as invalid (could check config.SUPPORTER_SESSIONS but we don't need)

        session_data = r.hgetall(session_token)
        if not session_data:
            return None

        if session_data.get("revoked") == "True":
            return None

        expires_at = datetime.fromisoformat(session_data["expires_at"])
        if datetime.utcnow() > expires_at:
            return None

        # Update usage
        audit_key = f"session_audit:{self._hash_token(session_token)}"
        r.hset(audit_key, mapping={
            "last_used": datetime.utcnow().isoformat(),
            "usage_count": str(int(r.hget(audit_key, "usage_count") or 0) + 1),
        })

        return {
            "id": session_data["id"],
            "license_id": session_data["license_id"],
            "tier": session_data["tier"],
            "issued_at": session_data["issued_at"],
            "expires_at": session_data["expires_at"],
            "revoked": session_data["revoked"] == "True",
        }

    def revoke_session(self, session_token: str) -> bool:
        """Revoke a session immediately."""
        r = self._get_redis()
        if not r:
            return False

        if not r.exists(session_token):
            return False

        r.hset(session_token, "revoked", "True")
        audit_key = f"session_audit:{self._hash_token(session_token)}"
        r.hset(audit_key, "revoked", "True")
        return True

Global instance
session_service = SessionService()


2.3 License API Endpoints (api/app/routers/license.py)

python
"""Endpoints for license activation, validation, and deactivation."""
from fastapi import APIRouter, Depends, Header, HTTPException, Request
from pydantic import BaseModel
from app.license_service import license_service, LicenseService
from app.session_service import session_service, SessionService

router = APIRouter(prefix="/api/license", tags=["license"])

class ActivateRequest(BaseModel):
    license_key: str

class ActivateResponse(BaseModel):
    session_token: str
    tier: str
    expires_in: int  # seconds until session expires

class ValidateResponse(BaseModel):
    valid: bool
    tier: str | None = None
    license_id: str | None = None
    expires_at: str | None = None  # license expiration

class DeactivateRequest(BaseModel):
    session_token: str

@router.post("/activate", response_model=ActivateResponse)
async def activate_license(
    request: ActivateRequest,
    license_svc: LicenseService = Depends(lambda: license_service),
    session_svc: SessionService = Depends(lambda: session_service),
):
    """
    Activate a license key and create a TukiWatch session.
    Frontend calls this after user enters their Lemon Squeezy license key.
    """
    license_data = await license_svc.validate_license(request.license_key)
    if not license_data:
        raise HTTPException(
            status_code=401,
            detail={"error": "Invalid license key", "type": "license_error"},
        )

    # Create short-lived session
    session_token = session_svc.create_session(
        license_id=license_data["license_id"],
        tier="supporter",  # Could derive from license data if multiple tiers
    )

    return ActivateResponse(
        session_token=session_token,
        tier="supporter",
        expires_in=session_service.SESSION_TTL_HOURS * 3600,
    )

@router.post("/validate", response_model=ValidateResponse)
async def validate_session(
    request: Request,
    authorization: str = Header(None),
    session_svc: SessionService = Depends(lambda: session_service),
):
    """
    Validate a session token (sent as Authorization: Bearer <token>).
    Used by middleware to determine supporter status.
    """
    if not authorization or not authorization.startswith("Bearer "):
        return ValidateResponse(valid=False)

    session_token = authorization.split(" ", 1)[1]
    session_data = session_svc.validate_session(session_token)

    if not session_data:
        return ValidateResponse(valid=False)

    return ValidateResponse(
        valid=True,
        tier=session_data["tier"],
        license_id=session_data["license_id"],
        expires_at=session_data["expires_at"],
    )

@router.post("/deactivate")
async def deactivate_license(
    request: DeactivateRequest,
    session_svc: SessionService = Depends(lambda: session_service),
):
    """Deactivate/revoke a session (logout)."""
    success = session_svc.revoke_session(request.session_token)
    if not success:
        raise HTTPException(
            status_code=400,
            detail={"error": "Invalid or already revoked session", "type": "session_error"},
        )
    return {"status": "revoked"}


2.4 Update Middleware to Use Session Validation

Modify api/app/middleware.py and api/app/auth.py:

api/app/auth.py (replace content):
python
"""Authentication utilities for supporter session validation."""
from typing import Optional
from fastapi import Request
from app.session_service import session_service, SessionService

async def get_supporter_info(request: Request) -> dict:
    """
    Extract and validate supporter session from request.
    Returns dict with 'is_supporter' and 'tier' fields.
    """
    authorization = request.headers.get("Authorization")
    is_supporter = False
    tier = "free"

    if authorization and authorization.startswith("Bearer "):
        token = authorization.split(" ", 1)[1]
        session_data = session_service.validate_session(token)
        if session_data:
            is_supporter = True
            tier = session_data.get("tier", "supporter")

    return {"is_supporter": is_supporter, "tier": tier}

def get_client_key(request: Request, is_supporter: bool, token: Optional[str] = None) -> str:
    """
    Generate rate limiting tracking key based on authentication tier.
    For supporters: use session token hash (not raw token) for privacy.
    """
    if is_supporter and token:
        # Hash the token for rate limiting key to avoid leaking session token in logs/metrics
        import hashlib
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


api/app/middleware.py: No changes needed beyond importing updated get_supporter_info and get_client_key (they already use these).

2.5 Update Rate Limit Config to Use-input Prefix

No change needed; get_client_key now returns session:<hash> for supporters.

2.6 Update Frontend to Use Sessions

Replace app/src/lib/supporterToken.ts with app/src/lib/sessionToken.ts:

typescript
// Secure storage for TukiWatch session tokens using expo-secure-store.
import * as SecureStore from 'expo-secure-store';

const SESSION_TOKEN_KEY = 'tukiwatch_session_token';

export async function getSessionToken(): Promise<string | null> {
  try {
    return await SecureStore.getItemAsync(SESSION_TOKEN_KEY);
  } catch {
    return null;
  }
}

export async function setSessionToken(token: string): Promise<void> {
  await SecureStore.setItemAsync(SESSION_TOKEN_KEY, token);
}

export async function clearSessionToken(): Promise<void> {
  await SecureStore.deleteItemAsync(SESSION_TOKEN_KEY);
}

export async function hasSessionToken(): Promise<boolean> {
  const token = await getSessionToken();
  return token !== null && token.length > 0;
}


Update app/src/lib/backendConfig.ts:
- Remove supporterToken?: string; from BackendConfig interface.
- Remove any logic that reads/writes supporterToken to/from AsyncStorage (it's not used anymore).
- Keep apiUrl and updateManifestUrl.

Update app/src/screens/Settings/BackendSettings.tsx:
- Replace supporterToken imports/usage with sessionToken.
- Change UI label from "Supporter Token" to "Session Token" or "License Key" (user enters license key, not session).
- Flow:
  1. User inputs Lemon Squeezy license key (not a UUID).
  2. On Save, call POST /api/license/activate with { license_key }.
  3. On success, store returned session_token via setSessionToken.
  4. Optionally show tier/expiry from response.
  5. For validation, use hasSessionToken() and call POST /api/license/validate with Authorization: Bearer <session>.
  6. Remove button calls clearSessionToken() and calls POST /api/license/deactivate (optional; can just rely on expiry).

Update app/src/lib/backendConfig.ts useBackendConfig hook: no changes needed (doesn't handle tokens).

Phase 3: Webhook Handler for License Events

Create api/app/webhooks.py:

python
"""Webhook handler for Lemon Squeezy events."""
import hmac
import hashlib
import logging
from typing import Any
from fastapi import APIRouter, Request, Header, HTTPException
from app.license_service import license_service
from app.session_service import session_service
from config import config

logger = logging.getLogger(name)
router = APIRouter()

WEBHOOK_SECRET = config.LEMON_SQUEEZY_WEBHOOK_SECRET.encode() if config.LEMON_SQUEEZY_WEBHOOK_SECRET else b""

@router.post("/webhooks/lemonsqueezy")
async def handle_ls_webhook(
    request: Request,
    signature: str = Header(None, alias="X-Signature"),
):
    """
    Verify and process Lemon Squeezy webhook events.
    Used to keep local session state in sync with subscription changes.
    """
    if not WEBHOOK_SECRET:
        logger.error("Webhook secret not configured")
        raise HTTPException(status_code=500, detail="Webhook misconfigured")

    raw_body = await request.body()

    # Verify HMAC signature
    mac = hmac.new(WEBHOOK_SECRET, msg=raw_body, digestmod=hashlib.sha256)
    expected_signature = mac.hexdigest()

    if not signature or not hmac.compare_digest(signature, expected_signature):
        logger.warning("Invalid webhook signature")
        raise HTTPException(status_code=401, detail="Invalid signature")

    try:
        event_data: Any = await request.json()
    except Exception:
        logger.error("Failed to parse webhook JSON")
        raise HTTPException(status_code=400, detail="Invalid payload")

    event_name = event_data.get("meta", {}).get("event_name")
    logger.info(f"Received Lemon Squeezy webhook: {event_name}")

    # We mainly care about subscription and license events to invalidate sessions
    # For simplicity, on any relevant event we could scan and revoke sessions for affected licenses,
    # but that's expensive. Instead, we rely on short session TTL and validation on each request.
    # If you want immediate revocation, you'd need to maintain a license->sessions mapping in Redis.

    # For now, we just log. In future, implement:
    # if event_name in ("subscription_cancelled", "subscription_expired", "license_key_expired"):
    #     license_id = event_data["data"]["relationships"]["license"]["data"]["id"]
    #     # Find and revoke all sessions for this license_id (requires reverse index)

    return {"status": "processed"}


Add to api/main.py:
python
from app.webhooks import router as webhook_router
...
app.include_router(webhook_router)


Phase 4: Update Tests

Remove api/tests/test_tokens.py and api/tests/test_supporters.py (if exists).

Update api/tests/test_middleware.py:
- Remove tests that reference X-Supporter-Token header.
- Add tests for Authorization: Bearer header.
- Ensure supporter tier gets higher limits via session token.
- Ensure /health endpoint excluded.

Update api/tests/test_rate_limits.py:
- Update to use session tokens (hashed keys) for supporter tests.
- Keep same logic.

Update api/tests/test_main.py: no changes needed.

Update api/tests/test_routers.py: no changes needed (streams unaffected).

Add new test file api/tests/test_license.py:
python
"""Tests for license activation and session creation."""
from unittest.mock import AsyncMock, patch
import pytest
from fastapi.testclient import TestClient
from main import app

client = TestClient(app)

def test_activate_invalid_license():
    resp = client.post("/api/license/activate", json={"license_key": "invalid"})
    assert resp.status_code == 401

def test_activate_valid_license(mocker):
    # Mock license_service.validate_license to return valid data
    mock_license_data = {
        "license_id": "lic_123",
        "license_key": "lskey_123",
        "status": "active",
        "product_id": "prod_123",  # should match config
        "variant_id": "var_123",   # should match config
    }
    with patch("app.license_service.license_service.validate_license", AsyncMock(return_value=mock_license_data)):
        with patch("app.session_service.session_service.create_session", return_value="sess_abc123"):
            resp = client.post("/api/license/activate", json={"license_key": "lskey_123"})
            assert resp.status_code == 200
            data = resp.json()
            assert data["session_token"] == "sess_abc123"
            assert data["tier"] == "supporter"
            assert data["expires_in"] == 24 * 3600  # 24 hours

def validate_session_endpoint():
    # Missing auth
    resp = client.post("/api/license/validate")
    assert resp.status_code == 200  # endpoint exists, returns valid=False
    assert resp.json()["valid"] is False

    # Invalid token
    resp = client.post(
        "/api/license/validate",
        headers={"Authorization": "Bearer invalid"},
    )
    assert resp.status_code == 200
    assert resp.json()["valid"] is False

    # Mock valid session
    with patch("app.session_service.session_service.validate_session", return_value={
        "id": "sess_abc123",
        "license_id": "lic_123",
        "tier": "supporter",
        "issued_at": "2025-01-01T00:00:00",
        "expires_at": "2025-01-02T00:00:00",
        "revoked": False,
    }):
        resp = client.post(
            "/api/license/validate",
            headers={"Authorization": "Bearer sess_abc123"},
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data["valid"] is True
        assert data["tier"] == "supporter"
        assert data["license_id"] == "lic_123"


Phase 5: Update Documentation

1. Update README.md:
   - Replace "Supporter Token" section with "License Key & Session".
   - Explain: User enters Lemon Squeezy license key in app → gets short-lived session → session used for API calls.
   - Note: License key is stored only by Lemon Squeezy; app only sees session token.

2. Update docs/SELF_HOSTING.md:
   - Add Lemon Squeezy setup steps (product, variant, webhook, API key).
   - Remove references to SendGrid, custom token generation, email-to-token mapping.
   - Add environment variables: LEMON_SQUEEZY_API_KEY, LEMON_SQUEEZY_STORE_ID, etc.
   - Note: Webhook endpoint is /webhooks/lemonsqueezy.

3. Update .env.example:
   dotenv
Lemon Squeezy
   LEMON_SQUEEZY_API_KEY=your_api_key_here
   LEMON_SQUEEZY_STORE_ID=your_store_id_here
   LEMON_SQUEEZY_PRODUCT_ID=your_product_id_here
   LEMON_SQUEEZY_VARIANT_ID=your_variant_id_here
   LEMON_SQUEEZY_WEBHOOK_SECRET=your_webhook_secret_here

Redis (optional, for caching/sessions/rate limiting)
   REDIS_URL=redis://localhost:6379/0

Other
   ALLOWED_ORIGINS=*
   

Phase 6: Security & Privacy Review

- [ ] No PII in logs: License key validation calls Lemon Squeezy API; we log only license_id, status, product/variant IDs (no customer email/name).
- [ ] No PII in rate limit keys: Supporter keys are session:<hash> where hash is truncated SHA-256 of session token.
- [ ] Session tokens short-lived (24h) and revocable.
- [ ] License key only entered once; never stored or transmitted by TukiWatch beyond initial activation call.
- [ ] Webhook signatures verified.
- [ ] License validation checks product and variant IDs to prevent cross-product license use.
- [ ] Error messages generic (e.g., "Invalid license key") to avoid leaking info about why invalid.

Phase 7: Rollout Steps

1. Manual: Set up Lemon Squeezy product as in Phase 1.
2. Backend:
   - Delete old token files.
   - Add license_service.py, session_service.py.
   - Replace auth.py content.
   - Update middleware.py imports (no logic change).
   - Add license.py router.
   - Add webhooks.py router.
   - Register new routers in main.py.
   - Update config.py (remove old vars, add new ones).
3. Frontend:
   - Replace supporterToken.ts with sessionToken.ts.
   - Update BackendSettings.tsx UI and logic.
   - Update backendConfig.ts (remove supporterToken field).
4. Tests:
   - Delete old token/test files.
   - Update middleware and rate limit tests.
   - Add license test file.
5. Env:
   - Update .env and .env.example.
6. Verify:
   - Run uv sync --all-groups
   - Run uv run pytest (should pass)
   - Manual flow:
     a. Enter valid Lemon Squeezy license key in Settings → Backend.
     b. App calls /api/license/activate → gets session token → stores in SecureStore.
     c. Subsequent API calls include Authorization: Bearer <session>.
     d. Middleware validates session → supporter tier → higher rate limits.
     e. After 24h, session expires → app must re-activate (or reuse license key to get new session).
     f. Webhook receipts logged (no action needed for basic flow; sessions expire naturally).

Phase 8: Future Improvements

- License-to-session mapping: Store in Redis to allow immediate session revocation on webhook events (e.g., subscription cancelled).
- Refresh token: Issue long-lived refresh token (stored SecureStore) to get new session without re-entering license key.
- Multiple device support: License API returns instances; limit activations per license.
- Offline validation: Cache license validation result for short period (with license_id) to reduce Lemon Squeezy API calls.



This implementation plan gives you a simple, secure, privacy-respecting system that leverages Lemon Squeezy for what it's good at (payments, subscriptions, licensing) while TukiWatch handles session management and API rate limiting. The crux is: never treat the license key as an API credential; always exchange it for a short-lived session token.
