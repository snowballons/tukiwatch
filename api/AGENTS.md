# Agent Instructions

This directory contains the TukiWatch FastAPI backend service.

## Quality gates

Before finishing any change:

```bash
uv sync --all-groups
uv run ruff check .
uv run pytest
```

## Session completion

Make sure all changes are committed and pushed from the repository root:

```bash
git add -A
git commit -m "..."
git push
```