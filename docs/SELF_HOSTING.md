# Self-Hosting the TukiWatch API

The TukiWatch backend is a **stateless** FastAPI service. It has no database:
the only state is an ephemeral cache (Redis, or an in-memory fallback) and the
streamlink session pool. "Backup" therefore means your configuration, nothing
more.

## Requirements

- Python 3.10+
- [uv](https://docs.astral.sh/uv/) (recommended) or pip
- Optional: Redis for a shared/durable-enough cache between instances

## Local run

```bash
cd api
cp .env.example .env   # set API_KEY, ALLOWED_ORIGINS, optionally Redis
uv sync --all-groups
uv run uvicorn main:app --host 0.0.0.0 --port 8000
```

Without Redis the cache falls back to in-memory, which is fine for a single
instance.

## Docker (docker-compose)

From the repository root:

```bash
cp api/.env.example .env
docker compose up -d
```

The root `docker-compose.yml` builds `api/` and exposes port `8000`. Set
`API_KEY` and `ALLOWED_ORIGINS` in your `.env` (or as environment variables).
Redis is optional — omit `REDIS_URL` to use the in-memory cache.

## Railway

1. Create a new Railway service from this repository.
2. Point the service **root directory** at `api/`.
3. Railway auto-detects `nixpacks.toml` and installs via `requirements.txt`.
4. Add a `PORT` variable (Railway provides it automatically) and set:
   - `API_KEY` — a long random string
   - `ALLOWED_ORIGINS` — e.g. `*`
   - Optionally attach a Railway Redis plugin and set `REDIS_URL`

## What to configure

| Variable | Required | Notes |
|---|---|---|
| `API_KEY` | Yes | Shared secret; the app sends it as `X-API-Key`. |
| `ALLOWED_ORIGINS` | No | Comma-separated CORS origins; default `*`. |
| `TWITCH_OAUTH_TOKEN` | No | Ad-free Twitch (Twitch Turbo). |
| `REDIS_URL` / `REDIS_*` | No | Ephemeral cache; omit for in-memory. |

## Clients

Point your mobile app at your instance:

- `EXPO_PUBLIC_API_URL` = `https://your-instance.example.com`
- `EXPO_PUBLIC_BACKEND_API_KEY` = the same `API_KEY` you set on the server

See the app README for the full distribution story (hosting your own APK +
`version.json`).

## Notes

- Redis holds only cached status results; it is **not** persistent storage.
- No user data is stored server-side — favorites live on-device in the app.