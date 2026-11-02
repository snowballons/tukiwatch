# Contributing to TukiWatch

Thank you for considering contributing to TukiWatch. This project is free and
open-source — contributions from the community are what keep it that way.

## Development Setup

### Prerequisites

- [Bun](https://bun.sh) >= 1.1
- Node.js >= 20
- A TukiWatch API instance (local or hosted) — see [`../docs/SELF_HOSTING.md`](../docs/SELF_HOSTING.md)

### Getting Started

1. Fork and clone the repository.
2. Install dependencies:

   ```sh
   cd app
   bun install
   ```

3. Create your local environment file:

   ```sh
   cp .env.example .env
   ```

   Set `EXPO_PUBLIC_API_URL` to match your API instance.

4. Start the development server:

   ```sh
   bun start
   ```

### Quality Gates

Before submitting a pull request, make sure the following pass locally:

```sh
bun install --frozen-lockfile   # reproducible install
bunx @biomejs/biome ci .        # lint + format check
bun run type-check              # TypeScript check
```

## Project Structure

- `lib/` — local data layer (SQLite favorites, export/import)
- `src/screens/` — screen components (Home, My List, Add, Settings, Player)
- `src/components/` — reusable UI components
- `src/services/` — API client and update logic
- `src/context/` — React context providers
- `src/hooks/` — custom hooks

## Data & Privacy

TukiWatch is local-first. Favorites are stored on-device in a SQLite database
(`app/lib/db.ts`). There are no accounts and no cloud sync. The app only talks
to your configured TukiWatch API to resolve stream status.

When adding features that touch data, keep it local-first: no new cloud
dependencies, no analytics, no third-party tracking.

## Style Notes

- TypeScript throughout, strict mode.
- Follow the formatting and lint rules enforced by Biome.
- Prefer small, focused files. Add comments sparingly and only when they add
  context.