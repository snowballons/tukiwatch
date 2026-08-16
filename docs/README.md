# TukiWatch Documentation

- [`app/`](app/) — Expo (React Native) mobile application
- [`api/`](api/) — FastAPI backend service

## Deploying the backend

The API ships with both a `Dockerfile` (used by `docker-compose.yml`) and a
`nixpacks.toml` (used by Railway). When deploying on Railway after the monorepo
merge, point the service's **root directory** at `api/`.