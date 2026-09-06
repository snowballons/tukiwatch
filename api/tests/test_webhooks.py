"""
Tests for app/webhooks.py (Polar webhook handler).

Covers:
- Missing/unknown secret, invalid signature, invalid JSON
- Standard Webhooks verification path + legacy X-Signature fallback
- subscription.revoked / benefit_grant.revoked revoke sessions
- canceled / past_due / refunded / state_changed leave sessions alive
"""

import base64
import hashlib
import hmac
import json
import time
from unittest.mock import patch

TEST_SECRET = "whsec_unittest"


def _sign(secret: str, msg_id: str, timestamp: str, body: bytes) -> str:
    """Compute a Standard Webhooks v1 signature for a test payload."""
    signed = f"{msg_id}.{timestamp}.".encode() + body
    digest = hmac.new(secret.encode(), signed, hashlib.sha256).digest()
    return f"v1,{base64.b64encode(digest).decode()}"


def _post_event(client, event_type: str, data: dict, secret: str = TEST_SECRET):
    """POST a signed webhook event; returns the response."""
    body = json.dumps({"type": event_type, "data": data}).encode()
    timestamp = str(int(time.time()))
    return client.post(
        "/webhooks/polar",
        content=body,
        headers={
            "Content-Type": "application/json",
            "webhook-id": "msg_test",
            "webhook-timestamp": timestamp,
            "webhook-signature": _sign(secret, "msg_test", timestamp, body),
        },
    )


def _make_session(memory_sessions, license_id="lic_hook", customer_id="cust_hook"):
    """Create a session on the hermetic store."""
    return memory_sessions.create_session(license_id, customer_id=customer_id)


# ===========================================================================
# Signature verification
# ===========================================================================


class TestWebhookSignature:
    """Tests for webhook authentication."""

    def test_missing_signature_returns_401(self, client):
        """Unsigned webhook posts are rejected."""
        with patch("app.webhooks.config.POLAR_WEBHOOK_SECRET", TEST_SECRET):
            response = client.post(
                "/webhooks/polar",
                json={"type": "subscription.revoked", "data": {}},
            )
        assert response.status_code == 401

    def test_wrong_signature_returns_401(self, client):
        """A signature made with another secret is rejected."""
        with patch("app.webhooks.config.POLAR_WEBHOOK_SECRET", TEST_SECRET):
            response = _post_event(
                client, "subscription.revoked", {}, secret="whsec_wrong"
            )
        assert response.status_code == 401

    def test_missing_secret_returns_500(self, client):
        """No configured secret is a server misconfiguration."""
        with patch("app.webhooks.config.POLAR_WEBHOOK_SECRET", ""):
            response = client.post(
                "/webhooks/polar",
                json={"type": "subscription.revoked", "data": {}},
            )
        assert response.status_code == 500

    def test_invalid_json_returns_400(self, client):
        """Signed but unparsable bodies are client errors."""
        body = b"{not-json"
        timestamp = str(int(time.time()))
        with patch("app.webhooks.config.POLAR_WEBHOOK_SECRET", TEST_SECRET):
            response = client.post(
                "/webhooks/polar",
                content=body,
                headers={
                    "Content-Type": "application/json",
                    "webhook-id": "msg_test",
                    "webhook-timestamp": timestamp,
                    "webhook-signature": _sign(TEST_SECRET, "msg_test", timestamp, body),
                },
            )
        assert response.status_code == 400

    def test_legacy_hmac_signature_accepted(self, client, memory_sessions):
        """The X-Signature fallback path verifies and processes."""
        body = json.dumps(
            {"type": "customer.state_changed", "data": {}}
        ).encode()
        signature = hmac.new(TEST_SECRET.encode(), body, hashlib.sha256).hexdigest()
        with (
            patch("app.webhooks.config.POLAR_WEBHOOK_SECRET", TEST_SECRET),
            patch("app.webhooks.session_service", memory_sessions),
        ):
            response = client.post(
                "/webhooks/polar",
                content=body,
                headers={
                    "Content-Type": "application/json",
                    "x-signature": signature,
                },
            )
        assert response.status_code == 200
        assert response.json() == {"status": "processed"}


# ===========================================================================
# Revocation semantics
# ===========================================================================


class TestWebhookRevocation:
    """Tests for which events end access (and which must not)."""

    def test_subscription_revoked_kills_session(self, client, memory_sessions):
        """subscription.revoked revokes the customer's sessions."""
        token = _make_session(memory_sessions)
        with (
            patch("app.webhooks.config.POLAR_WEBHOOK_SECRET", TEST_SECRET),
            patch("app.webhooks.session_service", memory_sessions),
        ):
            response = _post_event(
                client, "subscription.revoked", {"customer_id": "cust_hook"}
            )
        assert response.status_code == 200
        assert memory_sessions.validate_session(token) is None

    def test_benefit_grant_revoked_kills_session(self, client, memory_sessions):
        """benefit_grant.revoked revokes sessions by license id."""
        token = _make_session(memory_sessions)
        with (
            patch("app.webhooks.config.POLAR_WEBHOOK_SECRET", TEST_SECRET),
            patch("app.webhooks.session_service", memory_sessions),
        ):
            response = _post_event(
                client, "benefit_grant.revoked", {"license_key_id": "lic_hook"}
            )
        assert response.status_code == 200
        assert memory_sessions.validate_session(token) is None

    def test_non_terminal_events_keep_session(self, client, memory_sessions):
        """Canceled, past_due, refunded and state_changed must not revoke."""
        token = _make_session(memory_sessions)
        events = [
            ("subscription.canceled", {"customer_id": "cust_hook"}),
            ("subscription.past_due", {"customer_id": "cust_hook"}),
            ("subscription.cycled", {"customer_id": "cust_hook"}),
            ("order.refunded", {"customer_id": "cust_hook"}),
            ("customer.state_changed", {"customer_id": "cust_hook"}),
        ]
        with (
            patch("app.webhooks.config.POLAR_WEBHOOK_SECRET", TEST_SECRET),
            patch("app.webhooks.session_service", memory_sessions),
        ):
            for event_type, data in events:
                response = _post_event(client, event_type, data)
                assert response.status_code == 200, event_type
        assert memory_sessions.validate_session(token) is not None
