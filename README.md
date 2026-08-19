<div align="center">

<img src="https://tukiwatch.snowballons.com/logo.png" alt="TukiWatch logo" width="80">

# TukiWatch

**A privacy-first mobile app for tracking and watching live streams across platforms, backed by a self-hostable FastAPI backend.**

[![App CI](https://img.shields.io/github/actions/workflow/status/snowballons/tukiwatch/app.yml?branch=main&label=App%20CI&logo=github)](https://github.com/snowballons/tukiwatch/actions)
[![API CI](https://img.shields.io/github/actions/workflow/status/snowballons/tukiwatch/api.yml?branch=main&label=API%20CI&logo=github)](https://github.com/snowballons/tukiwatch/actions)
[![Release](https://img.shields.io/github/v/release/snowballons/tukiwatch?label=release)](https://github.com/snowballons/tukiwatch/releases)
[![License](https://img.shields.io/github/license/snowballons/tukiwatch?color=blue)](LICENSE)
[![Stars](https://img.shields.io/github/stars/snowballons/tukiwatch?style=social)](https://github.com/snowballons/tukiwatch)

[Download APK](https://github.com/snowballons/tukiwatch/releases) · [Web download page](https://tukiwatch.snowballons.com/)

</div>

---

## Table of Contents

<!--
  This TOC is hand-maintained. If it drifts, regenerate with `doctoc README.md`
  (npx doctoc) or drop it and rely on GitHub's built-in table of contents.
-->

- [About](#about)
- [Why This Exists](#why-this-exists)
- [Features](#features)
  - [Screenshots](#screenshots)
- [Non-Goals](#non-goals)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Repository Structure](#repository-structure)
- [Related Repositories](#related-repositories)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Quick Start](#quick-start)
- [Usage](#usage)
- [Troubleshooting](#troubleshooting)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Testing](#testing)
- [CI/CD](#cicd)
- [Deployment](#deployment)
- [Versioning & Changelog](#versioning--changelog)
- [Roadmap](#roadmap)
- [Project Status](#project-status)
- [Support](#support)
- [Contributing](#contributing)
- [Security](#security)
- [Credits & Acknowledgments](#credits--acknowledgments)
- [Author](#author)
- [License](#license)

---

---

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

---

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
