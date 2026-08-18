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

Point your mobile app at your instance — two ways:

### 1. Build-time default (rebuild required)

- `EXPO_PUBLIC_API_URL` = `https://your-instance.example.com`
- `EXPO_PUBLIC_BACKEND_API_KEY` = the same `API_KEY` you set on the server
- `EXPO_PUBLIC_UPDATE_MANIFEST_URL` = URL of your hosted `version.json` (see the
  app README for the full distribution story)

### 2. Runtime via QR code (no rebuild needed)

Published builds connect to a backend configured at runtime. From the app's
**Settings → Backend → Scan QR Code**, users scan a QR that encodes the connect
link. The QR content is a `tukiwatch://` URI carrying the base URL, the API key,
and the update-manifest URL:

```
tukiwatch://connect?url=https%3A%2F%2Fyour-instance.example.com&key=YOUR_API_KEY&updates=https%3A%2F%2Fyour-instance.example.com%2Fversion.json
```

The app validates the backend (pings `/health`, verifies the API key) before
saving. A scanned or deep-linked connect URI (`tukiwatch://connect?...`) is
applied automatically. The default (build-time) backend remains the fallback
until a QR is scanned, and **Settings → Use Default Server** resets to it.

Generate the QR with any QR tool (e.g. `qrencode`):

```bash
qrencode -o backend.png 'tukiwatch://connect?url=https%3A%2F%2Fyour-instance.example.com&key=YOUR_API_KEY&updates=https%3A%2F%2Fyour-instance.example.com%2Fversion.json'
```

Print the QR on your server / share it with your users; `key` is optional when
your `API_KEY` is empty.

A plain URL is also accepted — if the QR encodes just `https://your-instance.example.com`
(with no path), the app treats it as the backend base URL and connects without a key.
Use the full `tukiwatch://connect?...` form when you want to bundle the API key
and/or the update-manifest URL.

## Notes

- Redis holds only cached status results; it is **not** persistent storage.
- No user data is stored server-side — favorites live on-device in the app.
- The API key travels inside the connect URI/QR. Treat the QR as a secret —
  share it only with people you want to grant access.