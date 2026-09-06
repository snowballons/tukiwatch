"""
Tests for app/routers/license.py, app/polar_service.py and app/session_service.py.

Covers:
- POST /api/license/activate (invalid key, valid key, device-limit path)
- POST /api/license/validate (missing/bad/good/revoked sessions)
- POST /api/license/deactivate (revoke + unknown token)
- PolarService._normalize (grant/revoke/benefit/expiry/usage rules)
- SessionService round-trip + license/customer revocation indexes
"""

from unittest.mock import AsyncMock, patch

from app import polar_service as polar_service_module

VALID_LICENSE_DATA = {
    "key_id": "lic_test123",
    "status": "granted",
    "expires_at": None,
    "max_activations": 3,
    "current_activations": 1,
    "benefit_id": "ben_test",
    "customer_id": "cust_test9",
}

# ===========================================================================
# POST /api/license/activate
# ===========================================================================


class TestActivateLicense:
    """Tests for the license activation endpoint."""

    def test_invalid_license_returns_401(self, client):
        """An unknown license key must yield a generic 401."""
        with patch(
            "app.routers.license.polar_service.validate_key",
            AsyncMock(return_value=None),
        ):
            response = client.post(
                "/api/license/activate", json={"license_key": "TUKI_fake"}
            )
        assert response.status_code == 401
        body = response.json()
        assert body["detail"]["type"] == "license_error"

    def test_invalid_license_does_not_echo_key(self, client):
        """Error responses must never contain the submitted license key."""
        with patch(
            "app.routers.license.polar_service.validate_key",
            AsyncMock(return_value=None),
        ):
            response = client.post(
                "/api/license/activate", json={"license_key": "TUKI_secret123"}
            )
        assert "TUKI_secret123" not in response.text

    def test_valid_license_returns_session(self, client, memory_sessions):
        """A valid key mints a tw_sess_* token with supporter tier."""
        with (
            patch(
                "app.routers.license.polar_service.validate_key",
                AsyncMock(return_value=dict(VALID_LICENSE_DATA)),
            ),
            patch("app.routers.license.session_service", memory_sessions),
        ):
            response = client.post(
                "/api/license/activate", json={"license_key": "TUKI_real"}
            )
        assert response.status_code == 200
        body = response.json()
        assert body["session_token"].startswith("tw_sess_")
        assert body["tier"] == "supporter"
        assert body["expires_in"] == 24 * 3600

    def test_device_limit_returns_403(self, client, memory_sessions):
        """A full device slot with a label yields a 403, not a session."""
        full = dict(VALID_LICENSE_DATA, current_activations=3)
        with (
            patch(
                "app.routers.license.polar_service.validate_key",
                AsyncMock(return_value=full),
            ),
            patch(
                "app.routers.license.polar_service.activate_key",
                AsyncMock(return_value=None),
            ),
            patch("app.routers.license.session_service", memory_sessions),
        ):
            response = client.post(
                "/api/license/activate",
                json={"license_key": "TUKI_real", "label": "phone"},
            )
        assert response.status_code == 403
        assert response.json()["detail"]["type"] == "activation_limit_error"

    def test_blank_license_key_rejected(self, client):
        """Empty keys fail request validation with 422."""
        response = client.post("/api/license/activate", json={"license_key": ""})
        assert response.status_code == 422


# ===========================================================================
# POST /api/license/validate
# ===========================================================================


class TestValidateSession:
    """Tests for the session validation endpoint."""

    def test_missing_auth_returns_invalid(self, client):
        """No Authorization header means valid=False (still HTTP 200)."""
        response = client.post("/api/license/validate")
        assert response.status_code == 200
        assert response.json()["valid"] is False

    def test_malformed_auth_returns_invalid(self, client):
        """Non-Bearer credentials mean valid=False."""
        response = client.post(
            "/api/license/validate", headers={"Authorization": "Token abc"}
        )
        assert response.status_code == 200
        assert response.json()["valid"] is False

    def test_unknown_token_returns_invalid(self, client):
        """A well-formed but unknown session means valid=False."""
        response = client.post(
            "/api/license/validate",
            headers={"Authorization": "Bearer tw_sess_deadbeefdeadbeef"},
        )
        assert response.status_code == 200
        assert response.json()["valid"] is False

    def test_valid_session_returns_metadata(self, client, memory_sessions):
        """A live session returns tier, license id and expiry."""
        token = memory_sessions.create_session("lic_test123")
        with patch("app.routers.license.session_service", memory_sessions):
            response = client.post(
                "/api/license/validate",
                headers={"Authorization": f"Bearer {token}"},
            )
        assert response.status_code == 200
        body = response.json()
        assert body["valid"] is True
        assert body["tier"] == "supporter"
        assert body["license_id"] == "lic_test123"
        assert body["expires_at"]


# ===========================================================================
# POST /api/license/deactivate
# ===========================================================================


class TestDeactivateSession:
    """Tests for the session revocation endpoint."""

    def test_deactivate_revokes_session(self, client, memory_sessions):
        """Deactivation revokes; the token then validates as invalid."""
        token = memory_sessions.create_session("lic_test123")
        with patch("app.routers.license.session_service", memory_sessions):
            response = client.post(
                "/api/license/deactivate", json={"session_token": token}
            )
            assert response.status_code == 200
            assert response.json() == {"status": "revoked"}
            follow_up = client.post(
                "/api/license/validate",
                headers={"Authorization": f"Bearer {token}"},
            )
        assert follow_up.json()["valid"] is False

    def test_deactivate_unknown_token_returns_400(self, client, memory_sessions):
        """Revoking an unknown token is a client error."""
        with patch("app.routers.license.session_service", memory_sessions):
            response = client.post(
                "/api/license/deactivate",
                json={"session_token": "tw_sess_deadbeefdeadbeef"},
            )
        assert response.status_code == 400


# ===========================================================================
# PolarService._normalize
# ===========================================================================


class TestPolarNormalize:
    """Unit tests for license payload normalization (fail-closed rules)."""

    def _payload(self, **overrides):
        base = {
            "id": "lic_1",
            "status": "granted",
            "benefit_id": "ben_test",
            "expires_at": None,
            "limit_activations": 3,
            "activations": [{"id": "act_1"}],
            "usage": 0,
            "limit_usage": None,
            "customer_id": "cust_1",
        }
        base.update(overrides)
        return base

    def test_granted_key_normalizes(self):
        """A granted key for our benefit yields the normalized dict."""
        result = polar_service_module.polar_service._normalize(self._payload())
        assert result is not None
        assert result["key_id"] == "lic_1"
        assert result["current_activations"] == 1
        assert result["benefit_id"] == "ben_test"

    def test_revoked_key_rejected(self):
        """Revoked and disabled keys normalize to None."""
        assert polar_service_module.polar_service._normalize(
            self._payload(status="revoked")
        ) is None
        assert polar_service_module.polar_service._normalize(
            self._payload(status="disabled")
        ) is None

    def test_wrong_benefit_rejected(self):
        """A valid key for another product must not unlock TukiWatch."""
        assert polar_service_module.polar_service._normalize(
            self._payload(benefit_id="ben_other_product")
        ) is None

    def test_expired_key_rejected(self):
        """Past expires_at normalizes to None."""
        assert polar_service_module.polar_service._normalize(
            self._payload(expires_at="2020-01-01T00:00:00Z")
        ) is None

    def test_exhausted_quota_rejected(self):
        """Usage at/above limit_usage normalizes to None."""
        assert polar_service_module.polar_service._normalize(
            self._payload(usage=100, limit_usage=100)
        ) is None

    def test_explicit_invalid_flag_rejected(self):
        """A valid=false envelope normalizes to None."""
        assert polar_service_module.polar_service._normalize(
            self._payload(valid=False)
        ) is None


# ===========================================================================
# SessionService round-trip
# ===========================================================================


class TestSessionService:
    """Unit tests for session lifecycle and revocation indexes."""

    def test_create_validate_revoke_round_trip(self, memory_sessions):
        """Sessions validate until revoked or expired."""
        token = memory_sessions.create_session("lic_a", customer_id="cust_a")
        assert token.startswith("tw_sess_")
        assert memory_sessions.validate_session(token)["license_id"] == "lic_a"
        assert memory_sessions.revoke_session(token) is True
        assert memory_sessions.validate_session(token) is None

    def test_reject_malformed_tokens(self, memory_sessions):
        """Non-session strings never validate."""
        assert memory_sessions.validate_session("") is None
        assert memory_sessions.validate_session("TUKI_rawkey") is None
        assert memory_sessions.revoke_session("nope") is False

    def test_revoke_by_license(self, memory_sessions):
        """All sessions for a license id revoke together."""
        t1 = memory_sessions.create_session("lic_x", customer_id="cust_1")
        t2 = memory_sessions.create_session("lic_x", customer_id="cust_1")
        other = memory_sessions.create_session("lic_y", customer_id="cust_2")
        assert memory_sessions.revoke_sessions_for_license("lic_x") == 2
        assert memory_sessions.validate_session(t1) is None
        assert memory_sessions.validate_session(t2) is None
        assert memory_sessions.validate_session(other) is not None

    def test_revoke_by_customer(self, memory_sessions):
        """All sessions for a customer id revoke together."""
        t1 = memory_sessions.create_session("lic_1", customer_id="cust_gone")
        assert memory_sessions.revoke_sessions_for_customer("cust_gone") == 1
        assert memory_sessions.validate_session(t1) is None
        assert memory_sessions.revoke_sessions_for_customer("cust_nobody") == 0
