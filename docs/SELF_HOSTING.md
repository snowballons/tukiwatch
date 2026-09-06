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
cp .env.example .env   # set ALLOWED_ORIGINS, optionally Redis
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
`ALLOWED_ORIGINS` in your `.env` (or as an environment variable).
Redis is optional — omit `REDIS_URL` to use the in-memory cache.

## Railway

1. Create a new Railway service from this repository.
2. Point the service **root directory** at `api/`.
3. Railway auto-detects `nixpacks.toml` and installs via `requirements.txt`.
4. Add a `PORT` variable (Railway provides it automatically) and set:
   - `ALLOWED_ORIGINS` — e.g. `*`
   - Optionally attach a Railway Redis plugin and set `REDIS_URL`
   - For supporter licensing, set the `POLAR_*` variables (see "Supporter
     licensing (Polar)" below) — without them the deployment serves free tier
     only

### Railway with Redis (Recommended for Production)

For horizontal scaling and shared rate limiting across replicas, attach a Redis
plugin:

1. In Railway, click **New** → **Database** → **Add Redis**
2. Connect the Redis instance to your API service
3. Railway automatically injects `REDIS_URL` — no manual config needed
4. The API will use Redis for both **caching** and **distributed rate limiting**

Without Redis, each Railway replica uses independent in-memory rate limiting
and caching (fine for single-instance deployments).

## Hosted on Railway (Managed Endpoint)

For users who prefer not to self-host, a managed TukiWatch API is available at:

```
https://tukiwatch-api.up.railway.app
```

> **Note:** Replace with your actual Railway deployment URL.

### Connecting the Mobile App to the Hosted Endpoint

#### Option 1: QR Code (Recommended — No Rebuild Needed)

Published builds can switch backends at runtime via **Settings → Backend → Scan QR Code**.

Generate a QR code for the hosted endpoint:

```bash
qrencode -o hosted-backend.png 'tukiwatch://connect?url=https%3A%2F%2Ftukiwatch-api.up.railway.app&updates=https%3A%2F%2Ftukiwatch-api.up.railway.app%2Fversion.json'
```

Or use any online QR generator with this content:

```
tukiwatch://connect?url=https://tukiwatch-api.up.railway.app&updates=https://tukiwatch-api.up.railway.app/version.json
```

Scan the QR from the app's **Settings → Backend → Scan QR Code**. The app validates the backend (pings `/health`) before saving.

#### Option 2: Deep Link

Open this link on your phone to auto-connect:

```
tukiwatch://connect?url=https://tukiwatch-api.up.railway.app&updates=https://tukiwatch-api.up.railway.app/version.json
```

#### Option 3: Build-Time Default (Requires Rebuild)

Set `EXPO_PUBLIC_API_URL` at build time:

```bash
EXPO_PUBLIC_API_URL=https://tukiwatch-api.up.railway.app eas build --platform android
```

This bakes the hosted endpoint as the default. Users can still override at runtime via QR code.

## What to configure

| Variable | Required | Notes |
|---|---|---|
| `ALLOWED_ORIGINS` | No | Comma-separated CORS origins; default `*`. |
| `TWITCH_OAUTH_TOKEN` | No | Ad-free Twitch (Twitch Turbo). |
| `REDIS_URL` / `REDIS_*` | No | Ephemeral cache + distributed rate limiting; omit for in-memory. |
| `POLAR_ACCESS_TOKEN` / `POLAR_ORG_ID` / `POLAR_PRODUCT_ID` / `POLAR_BENEFIT_ID` | For supporter licensing | Your own Polar org/product/benefit (see below). Omit to serve the free tier only. |
| `POLAR_WEBHOOK_SECRET` | For supporter licensing | Signing secret for `POST /webhooks/polar`. |
| `POLAR_API_BASE` | No | Default `https://api.polar.sh`. |
| `SESSION_TTL_HOURS` / `SESSION_AUDIT_TTL_DAYS` | No | Session lifetime / audit retention; defaults 24h / 7d. |

## Supporter licensing (Polar)

The managed endpoint includes supporter licensing out of the box. Self-hosters
who want it must bring their own [Polar](https://polar.sh) setup:

1. Create a Polar organization, a recurring product (e.g. "TukiWatch
   Supporter"), and a **License Keys** benefit attached to it.
2. Create an Organization Access Token and a webhook signing secret.
3. Set the `POLAR_*` variables above (Railway: service **Variables** tab).
4. Register the webhook endpoint in Polar: `https://<your-host>/webhooks/polar`
   (Raw JSON; subscribe to `customer.state_changed`, `subscription.revoked`,
   `benefit_grant.created`/`benefit_grant.revoked`, `order.paid`,
   `order.refunded`).

Without `POLAR_*` the instance serves the free tier for everyone — license
endpoints answer "invalid" and rate limits stay IP-based. No Polar customer
data is stored: sessions are TTL'd hashes and the raw license key is never
persisted.

## Clients

Point your mobile app at your instance — two ways:

### 1. Build-time default (rebuild required)

- `EXPO_PUBLIC_API_URL` = `https://your-instance.example.com`
- `EXPO_PUBLIC_UPDATE_MANIFEST_URL` = URL of your hosted `version.json` (see the
  app README for the full distribution story)

### 2. Runtime via QR code (no rebuild needed)

Published builds connect to a backend configured at runtime. From the app's
**Settings → Backend → Scan QR Code**, users scan a QR that encodes the connect
link. The QR content is a `tukiwatch://` URI carrying the base URL and the
update-manifest URL:

```
tukiwatch://connect?url=https%3A%2F%2Fyour-instance.example.com&updates=https%3A%2F%2Fyour-instance.example.com%2Fversion.json
```

The app validates the backend (pings `/health`) before saving. A scanned or
deep-linked connect URI (`tukiwatch://connect?...`) is applied automatically.
The default (build-time) backend remains the fallback until a QR is scanned,
and **Settings → Use Default Server** resets to it.

Generate the QR with any QR tool (e.g. `qrencode`):

```bash
qrencode -o backend.png 'tukiwatch://connect?url=https%3A%2F%2Fyour-instance.example.com&updates=https%3A%2F%2Fyour-instance.example.com%2Fversion.json'
```

Print the QR on your server / share it with your users.

A plain URL is also accepted — if the QR encodes just
`https://your-instance.example.com` (with no path), the app treats it as the
backend base URL and connects directly. Connect URIs that still carry a
`key=` parameter (from older links) keep working — the key is ignored.

## Notes

- Redis holds only cached status results (plus TTL'd supporter sessions when
  licensing is enabled); it is **not** persistent storage.
- No accounts and no PII are stored server-side — favorites live on-device in
  the app, sessions are opaque hashes linked to Polar license/customer UUIDs.
- The API is **account-free**: anyone who can reach your backend URL gets the
  free tier. Rate limiting still applies, but protect the service at the
  network level (firewall / private tunnel) if you want to restrict access.
- **Rate limiting**: sliding window — per IP for free (`/resolve` 20/min,
  `/status-batch` 10/min, default 100/min), per session-hash for supporters
  (`/resolve` 200/min, `/status-batch` 100/min, default 1000/min). With Redis,
  limits are shared across all Railway replicas.