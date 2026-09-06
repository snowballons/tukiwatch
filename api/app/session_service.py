"""Short-lived TukiWatch session tokens (``tw_sess_*``).

A session proves "this installation recently presented a valid Polar license"
without ever using the license key as an API credential. Sessions live in
Redis when available, otherwise in process memory (same fallback pattern as
the rate limiter). See polar-plan1.md §5.2.
"""

import hashlib
import logging
import secrets
import time
from datetime import datetime, timedelta, timezone
from typing import Any

from config import config

logger = logging.getLogger(__name__)

TOKEN_PREFIX = "tw_sess_"
AUDIT_PREFIX = "session_audit:"
LICENSE_INDEX_PREFIX = "license_sessions:"
CUSTOMER_INDEX_PREFIX = "customer_sessions:"


class SessionService:
    """Create, validate, and revoke supporter session tokens."""

    def __init__(self) -> None:
        self._redis: Any | None = None
        self._redis_tried = False
        # In-memory fallback: token -> (data dict, expires_at epoch)
        self._memory: dict[str, tuple[dict[str, str], float]] = {}
        # license_id -> set of tokens (webhook revocation index)
        self._memory_index: dict[str, set[str]] = {}
        # customer_id -> set of tokens (webhook revocation index)
        self._memory_customer_index: dict[str, set[str]] = {}
        self._last_cleanup = time.time()

    # -- Redis plumbing ---------------------------------------------------

    def _get_redis(self) -> Any | None:
        if self._redis_tried:
            return self._redis
        self._redis_tried = True
        try:
            import redis

            if config.REDIS_URL:
                client = redis.Redis.from_url(
                    config.REDIS_URL, decode_responses=True
                )
            else:
                client = redis.Redis(
                    host=config.REDIS_HOST,
                    port=config.REDIS_PORT,
                    db=config.REDIS_DB,
                    password=config.REDIS_PASSWORD,
                    decode_responses=True,
                )
            client.ping()
            self._redis = client
            logger.info("Session store: Redis")
        except Exception as exc:
            self._redis = None
            logger.warning(
                "Redis unavailable for sessions (%s); using in-memory store", exc
            )
        return self._redis

    # -- Helpers ----------------------------------------------------------

    @staticmethod
    def _hash_token(token: str) -> str:
        return hashlib.sha256(token.encode()).hexdigest()

    def _ttl(self) -> timedelta:
        return timedelta(hours=config.SESSION_TTL_HOURS)

    def _audit_ttl(self) -> timedelta:
        return timedelta(days=config.SESSION_AUDIT_TTL_DAYS)

    def _cleanup_memory(self) -> None:
        now = time.time()
        if now - self._last_cleanup < 300:
            return
        for token in [t for t, (_, exp) in self._memory.items() if exp <= now]:
            del self._memory[token]
        self._last_cleanup = now

    # -- Public API -------------------------------------------------------

    def create_session(
        self,
        license_id: str,
        tier: str = "supporter",
        customer_id: str | None = None,
    ) -> str:
        """Mint a new session token linked to a Polar license id.

        ``customer_id`` is an opaque Polar UUID (no PII) used only as a
        webhook revocation index.
        """
        token = f"{TOKEN_PREFIX}{secrets.token_hex(8)}"
        now = datetime.now(timezone.utc)
        expires_at = now + self._ttl()
        data = {
            "id": token,
            "license_id": license_id,
            "tier": tier,
            "issued_at": now.isoformat(),
            "expires_at": expires_at.isoformat(),
            "revoked": "False",
        }

        redis_client = self._get_redis()
        if redis_client is not None:
            try:
                redis_client.hset(token, mapping=data)
                redis_client.expire(token, self._ttl())
                audit_key = f"{AUDIT_PREFIX}{self._hash_token(token)}"
                redis_client.hset(
                    audit_key,
                    mapping={
                        **data,
                        "session_hash": self._hash_token(token),
                        "created_at": now.isoformat(),
                        "last_used": "",
                        "usage_count": "0",
                    },
                )
                redis_client.expire(audit_key, self._audit_ttl())
                # Reverse indexes for webhook-driven revocation (§5.5).
                index_key = f"{LICENSE_INDEX_PREFIX}{license_id}"
                redis_client.sadd(index_key, token)
                redis_client.expire(index_key, self._audit_ttl())
                if customer_id:
                    customer_key = f"{CUSTOMER_INDEX_PREFIX}{customer_id}"
                    redis_client.sadd(customer_key, token)
                    redis_client.expire(customer_key, self._audit_ttl())
                return token
            except Exception as exc:
                logger.warning("Redis session write failed (%s); using memory", exc)
                self._redis = None

        self._cleanup_memory()
        self._memory[token] = (dict(data), expires_at.timestamp())
        self._memory_index.setdefault(license_id, set()).add(token)
        if customer_id:
            self._memory_customer_index.setdefault(customer_id, set()).add(token)
        return token

    def validate_session(self, session_token: str) -> dict[str, Any] | None:
        """Validate a session token; returns metadata or None."""
        if not session_token or not session_token.startswith(TOKEN_PREFIX):
            return None

        redis_client = self._get_redis()
        if redis_client is not None:
            try:
                data = redis_client.hgetall(session_token)
            except Exception as exc:
                logger.warning("Redis session read failed (%s)", exc)
                data = None
            if data:
                return self._check_data(session_token, data, redis_client)
            # Fall through to memory (token may predate a Redis reconnect).
            return self._validate_memory(session_token)

        return self._validate_memory(session_token)

    def _check_data(
        self, token: str, data: dict[str, str], redis_client: Any
    ) -> dict[str, Any] | None:
        if data.get("revoked") == "True":
            return None
        try:
            expires_at = datetime.fromisoformat(data["expires_at"])
        except (KeyError, ValueError):
            return None
        if datetime.now(timezone.utc) > expires_at:
            return None
        try:
            audit_key = f"{AUDIT_PREFIX}{self._hash_token(token)}"
            usage = int(redis_client.hget(audit_key, "usage_count") or 0) + 1
            redis_client.hset(
                audit_key,
                mapping={
                    "last_used": datetime.now(timezone.utc).isoformat(),
                    "usage_count": str(usage),
                },
            )
        except Exception as exc:
            logger.debug("Session audit bump failed (%s)", exc)
        return {
            "id": data["id"],
            "license_id": data["license_id"],
            "tier": data.get("tier", "supporter"),
            "issued_at": data["issued_at"],
            "expires_at": data["expires_at"],
            "revoked": False,
        }

    def _validate_memory(self, token: str) -> dict[str, Any] | None:
        self._cleanup_memory()
        entry = self._memory.get(token)
        if not entry:
            return None
        data, expires_at = entry
        if data.get("revoked") == "True" or time.time() > expires_at:
            return None
        return {
            "id": data["id"],
            "license_id": data["license_id"],
            "tier": data.get("tier", "supporter"),
            "issued_at": data["issued_at"],
            "expires_at": data["expires_at"],
            "revoked": False,
        }

    def revoke_session(self, session_token: str) -> bool:
        """Revoke a session immediately (logout)."""
        if not session_token or not session_token.startswith(TOKEN_PREFIX):
            return False
        revoked = False
        redis_client = self._get_redis()
        if redis_client is not None:
            try:
                if redis_client.exists(session_token):
                    redis_client.hset(session_token, "revoked", "True")
                    audit_key = f"{AUDIT_PREFIX}{self._hash_token(session_token)}"
                    redis_client.hset(audit_key, "revoked", "True")
                    revoked = True
            except Exception as exc:
                logger.warning("Redis session revoke failed (%s)", exc)
        entry = self._memory.get(session_token)
        if entry is not None:
            data, expires_at = entry
            data["revoked"] = "True"
            self._memory[session_token] = (data, expires_at)
            revoked = True
        return revoked

    def revoke_sessions_for_license(self, license_id: str) -> int:
        """Revoke every session minted for a Polar license id.

        Used by the Polar webhook handler (§5.5) on grant revocation.
        Returns the number of sessions revoked.
        """
        count = 0
        redis_client = self._get_redis()
        if redis_client is not None:
            try:
                index_key = f"{LICENSE_INDEX_PREFIX}{license_id}"
                tokens = redis_client.smembers(index_key) or set()
                for token in tokens:
                    if self.revoke_session(str(token)):
                        count += 1
                redis_client.delete(index_key)
                return count
            except Exception as exc:
                logger.warning("Redis license-index revoke failed (%s)", exc)
        for token in list(self._memory_index.get(license_id, set())):
            if self.revoke_session(token):
                count += 1
        self._memory_index.pop(license_id, None)
        return count


    def revoke_sessions_for_customer(self, customer_id: str) -> int:
        """Revoke every session linked to a Polar customer id.

        Used by the Polar webhook handler (§5.5) on subscription revocation.
        Returns the number of sessions revoked.
        """
        if not customer_id:
            return 0
        count = 0
        redis_client = self._get_redis()
        if redis_client is not None:
            try:
                index_key = f"{CUSTOMER_INDEX_PREFIX}{customer_id}"
                tokens = redis_client.smembers(index_key) or set()
                for token in tokens:
                    if self.revoke_session(str(token)):
                        count += 1
                redis_client.delete(index_key)
                return count
            except Exception as exc:
                logger.warning("Redis customer-index revoke failed (%s)", exc)
        for token in list(self._memory_customer_index.get(customer_id, set())):
            if self.revoke_session(token):
                count += 1
        self._memory_customer_index.pop(customer_id, None)
        return count


# Global instance
session_service = SessionService()
