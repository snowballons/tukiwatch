"""Twitch Helix API client for Discovery feature"""

import asyncio
import time
from dataclasses import dataclass
from typing import Any

import httpx

from config import config


@dataclass
class TwitchToken:
    access_token: str
    expires_at: float  # Unix timestamp


class TwitchHelixClient:
    """Client for Twitch Helix API using App Access Token flow"""

    BASE_URL = "https://api.twitch.tv/helix"
    TOKEN_URL = "https://id.twitch.tv/oauth2/token"

    def __init__(self):
        self.client_id = config.TWITCH_CLIENT_ID
        self.client_secret = config.TWITCH_CLIENT_SECRET
        self._token: TwitchToken | None = None
        self._client: httpx.AsyncClient | None = None
        self._token_lock = asyncio.Lock()

    async def _get_client(self) -> httpx.AsyncClient:
        if self._client is None:
            self._client = httpx.AsyncClient(timeout=30.0)
        return self._client

    async def close(self):
        if self._client:
            await self._client.aclose()
            self._client = None

    async def _get_app_access_token(self) -> str:
        """Get or refresh App Access Token (Client Credentials flow)"""
        async with self._token_lock:
            # Check if we have a valid token
            if self._token and self._token.expires_at > time.time() + 60:  # 60s buffer
                return self._token.access_token

            if not self.client_id or not self.client_secret:
                raise ValueError(
                    "TWITCH_CLIENT_ID and TWITCH_CLIENT_SECRET must be configured"
                )

            client = await self._get_client()
            response = await client.post(
                self.TOKEN_URL,
                data={
                    "client_id": self.client_id,
                    "client_secret": self.client_secret,
                    "grant_type": "client_credentials",
                },
                headers={"Content-Type": "application/x-www-form-urlencoded"},
            )
            response.raise_for_status()
            data = response.json()

            self._token = TwitchToken(
                access_token=data["access_token"],
                expires_at=time.time() + data["expires_in"],
            )
            return self._token.access_token

    def _get_headers(self, token: str) -> dict[str, str]:
        return {
            "Authorization": f"Bearer {token}",
            "Client-Id": self.client_id,
        }

    async def get_streams(
        self,
        first: int = 20,
        after: str | None = None,
        game_id: str | None = None,
        language: str | None = None,
        user_login: list[str] | None = None,
    ) -> dict[str, Any]:
        """Get live streams from Twitch Helix API

        Args:
            first: Number of streams to return (max 100)
            after: Cursor for pagination
            game_id: Filter by game/category ID
            language: Filter by language (e.g., 'en')
            user_login: Filter by specific streamer logins (up to 100)
        """
        token = await self._get_app_access_token()
        client = await self._get_client()

        params: dict[str, Any] = {"first": min(first, 100), "type": "live"}
        if after:
            params["after"] = after
        if game_id:
            params["game_id"] = game_id
        if language:
            params["language"] = language
        if user_login:
            params["user_login"] = user_login[:100]

        response = await client.get(
            f"{self.BASE_URL}/streams",
            params=params,
            headers=self._get_headers(token),
        )

        # Handle rate limiting
        if response.status_code == 429:
            reset_time = int(response.headers.get("Ratelimit-Reset", "0"))
            retry_after = max(reset_time - int(time.time()), 1)
            raise httpx.HTTPStatusError(
                f"Rate limited. Retry after {retry_after} seconds",
                request=response.request,
                response=response,
            )

        response.raise_for_status()
        return response.json()

    async def get_top_games(
        self, first: int = 20, after: str | None = None
    ) -> dict[str, Any]:
        """Get top games/categories from Twitch Helix API"""
        token = await self._get_app_access_token()
        client = await self._get_client()

        params: dict[str, Any] = {"first": min(first, 100)}
        if after:
            params["after"] = after

        response = await client.get(
            f"{self.BASE_URL}/games/top",
            params=params,
            headers=self._get_headers(token),
        )
        response.raise_for_status()
        return response.json()

    async def search_categories(self, query: str, first: int = 20) -> dict[str, Any]:
        """Search for categories/games"""
        token = await self._get_app_access_token()
        client = await self._get_client()

        params = {"query": query, "first": min(first, 100)}
        response = await client.get(
            f"{self.BASE_URL}/search/categories",
            params=params,
            headers=self._get_headers(token),
        )
        response.raise_for_status()
        return response.json()


# Global instance
_twitch_client: TwitchHelixClient | None = None


def get_twitch_client() -> TwitchHelixClient:
    global _twitch_client
    if _twitch_client is None:
        _twitch_client = TwitchHelixClient()
    return _twitch_client


async def close_twitch_client():
    global _twitch_client
    if _twitch_client:
        await _twitch_client.close()
        _twitch_client = None
