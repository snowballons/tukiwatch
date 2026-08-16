# Agent Instructions

This repository is a monorepo for TukiWatch:

- `app/` — Expo (React Native) mobile application
- `api/` — FastAPI backend service

## Quality gates

Before finishing any change, run the relevant checks:

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
bun install --frozen-lockfile
bunx @biomejs/biome ci .
bun run type-check
```

## Session completion

When ending a work session, make sure all changes are committed and pushed:

```bash
git add -A
git commit -m "..."
git push
```