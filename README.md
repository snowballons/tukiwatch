# TukiWatch

TukiWatch is a cross-platform mobile app for finding and watching live streams, powered by a FastAPI backend.

## Repository layout

```
.
├── app/    # Expo (React Native) mobile application
├── api/    # FastAPI backend service
└── docs/   # Documentation
```

## Related repositories

- [streamwatch-core](https://github.com/snowballons/streamwatch-core) — shared core library (rules, resolvers) used by the API and CLI
- [streamwatch-cli](https://github.com/snowballons/streamwatch-cli) — command-line interface for resolving and watching streams

## Quick start

### Backend (`api/`)

```bash
cd api
uv sync --all-groups
uv run ruff check .
uv run pytest
```

### App (`app/`)

```bash
cd app
bun install
bunx @biomejs/biome ci .
bun run type-check
```

## CI

GitHub Actions workflows live in `.github/workflows/` at the repository root:

- `api.yml` — runs on changes under `api/**`: uv sync, ruff, pytest (Python 3.10–3.12)
- `app.yml` — runs on changes under `app/**`: bun install, biome ci, type-check
