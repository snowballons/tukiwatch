# TukiWatch API

FastAPI backend for [TukiWatch](https://github.com/snowballons/tukiwatch) — resolves live-stream URLs and checks stream status across platforms (Twitch, YouTube, Kick, and more via [streamlink](https://streamlink.github.io/)).

- **Stateless** — no database. State is limited to an ephemeral cache (Redis or in-memory) and the streamlink session pool.
- **Shared domain logic** comes from [streamwatch-core](https://github.com/snowballons/streamwatch-core) (pip dependency), the canonical package also vendored by the CLI.
- Built for self-hosting: Docker, Railway, or plain `uv run uvicorn`.

## Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/status` | Batch check stream status |
| `GET` | `/api/resolve/{url}` | Resolve a stream URL to playable streams + metadata |
| `GET` | `/health` | Liveness probe |
| `GET` | `/cache/stats` | Cache statistics |
| `GET` | `/rate-limit/stats` | Rate-limit configuration summary |
| `GET` | `/session/stats` | Streamlink session pool statistics |

All requests must send the API key in the `X-API-Key` header.

## Quick start

```bash
cd api
cp .env.example .env   # set API_KEY
uv sync --all-groups
uv run uvicorn main:app --host 0.0.0.0 --port 8000
```

## Development

```bash
uv sync --all-groups
uv run ruff check .
uv run pytest
```

## Deploy

- Docker / docker-compose and Railway instructions: [docs/SELF_HOSTING.md](../docs/SELF_HOSTING.md)
- `requirements.txt` is used by `nixpacks.toml` (Railway); `uv.lock` is used by Docker and local dev.

## Related

- [streamwatch-core](https://github.com/snowballons/streamwatch-core) — shared domain package
- [streamwatch-cli](https://github.com/snowballons/streamwatch-cli) — standalone terminal client
- [TukiWatch app](../app/README.md) — Expo / React Native mobile client

## License

[MIT](../LICENSE)