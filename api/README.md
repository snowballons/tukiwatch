# TukiWatch API

Stateless [FastAPI](https://fastapi.tiangolo.com/) backend for
[TukiWatch](../README.md) — resolves live-stream URLs and batch-checks stream
status across platforms (Twitch, YouTube, Kick, and more) via
[streamlink](https://streamlink.github.io/) and the shared
[streamwatch-core](https://github.com/snowballons/streamwatch-core) package.

- **Stateless** — no database. State is limited to an ephemeral cache (Redis or
  an in-memory fallback) and a recycled streamlink session pool.
- **Unauthenticated** — anyone who can reach the URL can use it (rate limits
  still apply; protect at the network level if needed).
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

Copy `.env.example` → `.env` and fill in the values. Redis is an ephemeral
cache only — no data lives there permanently.

## Internals

- **Cache TTLs** (`app/cache.py`): status results 120s, status errors 30s,
  full resolution 300s, offline resolution 60s.
- **Rate limits** (`app/rate_limit.py`, per IP):
  `/resolve` 20/min · `/status-batch` 10/min · `/health` 200/min ·
  `/cache/stats` 50/min · default 100/min. Responses carry
  `X-RateLimit-Limit/Remaining/Reset` headers; over-limit returns `429` with a
  `retry_after`.
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
