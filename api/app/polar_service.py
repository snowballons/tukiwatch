"""Adapter around Polar's server-side license key API.

Polar owns key generation, billing lifecycle, and grant/revoke. This module
only answers one question: "is this TUKI_ key currently usable?" — it never
stores keys and never logs the raw key, customer email, or full responses.

Uses the private merchant path (``/v1/license-keys/*``) with the Organization
Access Token so validation happens server-side. See polar-plan1.md §5.1.
"""

import logging
from datetime import datetime, timezone
from typing import Any

import httpx

from config import config

logger = logging.getLogger(__name__)

REQUEST_TIMEOUT = 30.0


class PolarService:
    """Thin client for Polar license key validation/activation."""

    def _base_url(self) -> str:
        return config.POLAR_API_BASE.rstrip("/")

    def _headers(self) -> dict[str, str]:
        return {
            "Accept": "application/json",
            "Content-Type": "application/json",
            "Authorization": f"Bearer {config.POLAR_ACCESS_TOKEN}",
        }

    def _configured(self) -> bool:
        """Check Polar credentials are present (sandbox or prod)."""
        return bool(config.POLAR_ACCESS_TOKEN and config.POLAR_ORG_ID)

    async def _post(self, path: str, payload: dict[str, Any]) -> dict[str, Any] | None:
        if not self._configured():
            logger.error("Polar is not configured (missing access token or org ID)")
            return None
        try:
            async with httpx.AsyncClient(
                base_url=self._base_url(),
                headers=self._headers(),
                timeout=REQUEST_TIMEOUT,
            ) as client:
                response = await client.post(path, json=payload)
        except httpx.HTTPError as exc:
            logger.warning("Polar API transport error: %s", type(exc).__name__)
            return None
        if response.status_code == 404:
            return None  # Unknown/invalid key — fail closed, no log detail.
        if response.status_code in (401, 403):
            logger.error(
                "Polar API rejected our credentials (status %s); check POLAR_ACCESS_TOKEN",
                response.status_code,
            )
            return None
        if response.status_code == 422:
            logger.error("Polar API rejected validate payload shape (status 422)")
            return None
        try:
            response.raise_for_status()
        except httpx.HTTPStatusError as exc:
            logger.warning("Polar API error: status %s", exc.response.status_code)
            return None
        try:
            data = response.json()
        except ValueError:
            logger.warning("Polar API returned non-JSON response")
            return None
        return data if isinstance(data, dict) else None

    def _normalize(self, payload: dict[str, Any]) -> dict[str, Any] | None:
        """Reduce a Polar license payload to the fields TukiWatch cares about.

        Defensive: Polar may nest benefit/customer objects or use alternate
        key names across API versions. Returns None when the key is not usable.
        """
        if isinstance(payload.get("valid"), bool) and not payload["valid"]:
            return None

        status = payload.get("status")
        if status != "granted":
            # "revoked" is terminal, "disabled" is suspended; anything else
            # is unknown — fail closed in all cases.
            return None

        benefit = payload.get("benefit") or {}
        benefit_id = payload.get("benefit_id") or (
            benefit.get("id") if isinstance(benefit, dict) else None
        )
        if config.POLAR_BENEFIT_ID and benefit_id != config.POLAR_BENEFIT_ID:
            # A valid Polar key for a *different* product must not unlock us.
            logger.warning("License benefit mismatch (benefit_id does not match)")
            return None

        expires_at = payload.get("expires_at")
        if expires_at:
            try:
                expiry = datetime.fromisoformat(str(expires_at).replace("Z", "+00:00"))
                if expiry <= datetime.now(timezone.utc):
                    return None
            except ValueError:
                logger.warning("License has unparsable expires_at; failing closed")
                return None

        limit_usage = payload.get("limit_usage")
        usage = payload.get("usage") or 0
        if limit_usage is not None:
            try:
                if float(usage) >= float(limit_usage):
                    return None
            except (TypeError, ValueError):
                return None

        activations = payload.get("activations")
        current_activations = (
            len(activations)
            if isinstance(activations, list)
            else int(payload.get("current_activations") or 0)
        )

        customer = payload.get("customer") or {}
        return {
            "key_id": payload.get("id"),
            "status": status,
            "expires_at": expires_at,
            "max_activations": payload.get("limit_activations"),
            "current_activations": current_activations,
            "benefit_id": benefit_id,
            "customer_id": payload.get("customer_id")
            or (customer.get("id") if isinstance(customer, dict) else None),
        }

    async def validate_key(
        self, license_key: str, activation_id: str | None = None
    ) -> dict[str, Any] | None:
        """Validate a license key (read-only; consumes no usage quota).

        Returns the normalized license dict, or None when invalid/unusable.
        """
        if not license_key or not license_key.strip():
            return None
        payload: dict[str, Any] = {
            "key": license_key.strip(),
            "organization_id": config.POLAR_ORG_ID,
            "benefit_id": config.POLAR_BENEFIT_ID,
            "increment_usage": 0,
        }
        if activation_id:
            payload["activation_id"] = activation_id
        data = await self._post("/v1/license-keys/validate", payload)
        if data is None:
            return None
        result = self._normalize(data)
        if result is None:
            return None
        logger.info(
            "License validation succeeded (key_id=%s status=%s)",
            result["key_id"],
            result["status"],
        )
        return result

    async def activate_key(
        self,
        license_key: str,
        label: str,
        conditions: dict[str, Any] | None = None,
        meta: dict[str, Any] | None = None,
    ) -> dict[str, Any] | None:
        """Pin a license key to one device/installation (one activation slot).

        Only call when enforcing the device limit. Returns the activation
        record (including ``activation_id``), or None on failure/limit hit.
        """
        if not license_key or not license_key.strip() or not label:
            return None
        payload: dict[str, Any] = {
            "key": license_key.strip(),
            "organization_id": config.POLAR_ORG_ID,
            "label": label,
        }
        if conditions:
            payload["conditions"] = conditions
        if meta:
            payload["meta"] = meta
        data = await self._post("/v1/license-keys/activate", payload)
        if data is None:
            return None
        activation_id = data.get("id")
        if not activation_id:
            logger.warning("Polar activation response missing activation id")
            return None
        logger.info("License activation created (activation_id=%s)", activation_id)
        return {
            "activation_id": activation_id,
            "label": data.get("label"),
            "created_at": data.get("created_at"),
        }


# Global instance
polar_service = PolarService()
