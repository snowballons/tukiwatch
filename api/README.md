# TukiWatch API

Stateless [FastAPI](https://fastapi.tiangolo.com/) backend for
[TukiWatch](../README.md) — resolves live-stream URLs and batch-checks stream
status across platforms (Twitch, YouTube, Kick, and more) via
[streamlink](https://streamlink.github.io/) and the shared
[streamwatch-core](https://github.com/snowballons/streamwatch-core) package.

- **Stateless** — no database. State is limited to an ephemeral cache (Redis or
  an in-memory fallback), short-lived supporter sessions (24h TTL), and a
  recycled streamlink session pool.
- **Account-free** — no usernames or passwords. Anyone who can reach the URL
  can use it on the free tier (rate limits still apply; protect at the network
  level if needed). Supporters unlock higher limits with a Polar license key,
  exchanged once for a short-lived session token
  (`Authorization: Bearer tw_sess_*`).
- Built for self-hosting: [Docker](../docs/SELF_HOSTING.md), Railway, or plain
  `uv run uvicorn`.

## Requirements

- Python 3.10+ and [uv](https://docs.astral.sh/uv/) (or pip)
- Optional: [Redis](https://redis.io/) for a shared cache between instances
  (falls back to in-memory without it)

## Quick start

```bash
cd api
cp .env.example .env   # set ALLOWED_ORIGINS, optionally Redis / Twitch token
uv sync --all-groups
uv run uvicorn main:app --host 0.0.0.0 --port 8000
```

## Endpoints

| Method | Path | Description |
| :--- | :--- | :--- |
| `GET` | `/` | Service info (`{"status":"ok","service":"streamlink-api"}`) |
| `GET` | `/health` | Liveness probe — used by the app to verify a backend |
| `GET` | `/api/resolve?url=<url>&bypass_cache=` | Resolve a stream URL to playable streams + metadata |
| `POST` | `/api/status-batch?bypass_cache=` | Batch-check stream status; body `{"urls": ["<url>", …]}` |
| `GET` | `/cache/stats` | Cache statistics |
| `GET` | `/rate-limit/stats` | Rate-limit configuration summary |
| `GET` | `/session/stats` | Streamlink session-pool statistics |
| `POST` | `/api/license/activate` | Exchange a Polar license key for a session token; body `{"license_key": "TUKI_…"}` |
| `POST` | `/api/license/validate` | Validate a session (`Authorization: Bearer tw_sess_*`) → `{"valid": true/false, …}` |
| `POST` | `/api/license/deactivate` | Revoke a session (logout); body `{"session_token": "tw_sess_…"}` |
| `POST` | `/webhooks/polar` | Polar webhook receiver (subscriptions, benefit grants, orders) |

`bypass_cache=true` clears the cache entry before serving a fresh result.

### `GET /api/resolve`

Response — `status` is `online`, `offline`, or `error`:

```json
{
  "status": "online",
  "title": "Stream title",
  "author": "Channel name",
  "thumbnail": "https://…",
  "best_quality": "https://…/best.m3u8",
  "all_qualities": { "720p": "https://…", "best": "https://…" },
  "category": "Just Chatting",
  "stream_id": "…",
  "platform": "twitch",
  "stream_types": ["hls", "rtmp"],
  "original_url": "https://twitch.tv/…",
  "_cached": false
}
```

Errors include a structured `error_details` object (e.g.
`{"type":"browser_required", "message":…}` for platforms behind anti-bot
protection).

### `POST /api/status-batch`

Body: `{"urls": ["https://twitch.tv/…", "https://youtube.com/…"]}`

Response — one entry per URL; per-entry `status` is `online`, `offline`, or
`error`:

```json
{
  "results": [
    { "url": "https://twitch.tv/…", "status": "online", "title": "…", "author": "…", "thumbnail": "…", "platform": "twitch", "_cached": false },
    { "url": "https://youtube.com/…", "status": "offline", "platform": "youtube" }
  ]
}
```

## Configuration

| Variable | Description | Required | Default |
| :--- | :--- | :---: | :--- |
| `ALLOWED_ORIGINS` | Comma-separated CORS origins | No | `*` |
| `TWITCH_OAUTH_TOKEN` | Twitch OAuth token for ad-free streams (Twitch Turbo) | No | — |
| `REDIS_URL` | Redis connection string (shared cache) | No | in-memory cache |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_DB` / `REDIS_PASSWORD` | Redis connection details when `REDIS_URL` is not set | No | localhost / 6379 / 0 / — |
| `POLAR_ACCESS_TOKEN` | Polar Organization Access Token (license validation) | No¹ | — |
| `POLAR_ORG_ID` | Polar organization ID | No¹ | — |
| `POLAR_PRODUCT_ID` | Polar "TukiWatch Supporter" product ID | No¹ | — |
| `POLAR_BENEFIT_ID` | Polar license-keys benefit ID | No¹ | — |
| `POLAR_WEBHOOK_SECRET` | Polar webhook signing secret | No¹ | — |
| `POLAR_API_BASE` | Polar API base URL | No | `https://api.polar.sh` |
| `SESSION_TTL_HOURS` | Supporter session lifetime (hours) | No | `24` |
| `SESSION_AUDIT_TTL_DAYS` | Session audit record retention (days) | No | `7` |

¹Required only to enable supporter licensing. Without them the API serves the
free tier for everyone and license endpoints return "invalid".

Copy `.env.example` → `.env` and fill in the values. Redis is an ephemeral
cache only — no data lives there permanently. Supporter sessions are likewise
ephemeral (TTL'd hashes; the raw Polar key is never stored).

## Internals

- **Cache TTLs** (`app/cache.py`): status results 120s, status errors 30s,
  full resolution 300s, offline resolution 60s.
- **Rate limits** (`app/rate_limit.py`): free tier is tracked per IP
  (`/resolve` 20/min · `/status-batch` 10/min · default 100/min); supporter
  tier is tracked per session hash (`/resolve` 200/min · `/status-batch`
  100/min · default 1000/min). Responses carry
  `X-RateLimit-Limit/Remaining/Reset` headers (plus `X-Supporter-Tier` for
  supporters); over-limit returns `429` with a `retry_after`.
- **Supporter licensing** (`app/polar_service.py`, `app/session_service.py`,
  `app/routers/license.py`, `app/webhooks.py`): Polar owns keys, billing, tax
  and dunning; the API exchanges a `TUKI_` key once for a `tw_sess_*` session
  and syncs revocations via `POST /webhooks/polar` (revoke only on
  `benefit_grant.revoked` / `subscription.revoked`).
- **Session pool** (`app/session_pool.py`): recycled streamlink sessions avoid
  connection overhead; Twitch sessions get low-latency options and an optional
  OAuth header for ad-free playback.
- Errors never crash the service — validation failures and plugin errors become
  structured HTTP responses.

## Deploy

- Docker Compose / Railway: [`../docs/SELF_HOSTING.md`](../docs/SELF_HOSTING.md)
- `requirements.txt` is used by `nixpacks.toml` (Railway); `uv.lock` is used by
  Docker and local dev.

## Quality gates

```bash
uv sync --all-groups
uv run ruff check .
uv run pytest
```

## Related

- [TukiWatch](../README.md) — repository root
- [TukiWatch App](../app/README.md) — the Expo / React Native client
- [streamwatch-core](https://github.com/snowballons/streamwatch-core) — shared
  domain package (rules, resolvers)
- [streamwatch-cli](https://github.com/snowballons/streamwatch-cli) — terminal
  client built on the same core

## License

[MIT](../LICENSE) — Copyright (c) 2026 Snowballons.
