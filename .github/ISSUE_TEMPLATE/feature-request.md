---
name: Feature request
about: Suggest an improvement or new capability. TukiWatch is local-first with no backend — read the constraints below before proposing something that needs a server.
title: "[Feature] <short summary>"
labels: enhancement
assignees: ''
---

## The idea

A clear, one-sentence summary of what you'd like TukiWatch to do.

## Why it matters

What problem does this solve, or what would it improve? If you have a specific use case, describe it.

## What it would take

TukiWatch is **Android-only, local-first, and has no backend or account system** — favourites and settings live on-device in SQLite/DataStore, and stream resolution runs on-device through Streamlink. Before proposing something, consider whether it fits those constraints:

- **No server, no cloud sync, no analytics.** Anything requiring a backend is out of scope for the app itself.
- **No JavaScript/Node tooling in the Android app.**
- **No API secrets bundled in the APK.** Platform credentials are supplied by the user and stored locally.

If your idea needs a backend, say so anyway — it may still be worth discussing, but it won't ship as-is.

## Examples of in-scope ideas

- A new default quality or codec preference.
- A new platform preset in the Add stream picker.
- A new preference or UI tweak in Settings.
- Better handling of a network error or stream state.
- A `tukiwatch://` deep-link scheme for a new action.

## Examples of out-of-scope ideas (for now)

- A web client, iOS client, or desktop client (the desktop harness is dev-only).
- User accounts, cloud favourites, or cross-device sync.
- In-app analytics or telemetry.
- A general-purpose media player unrelated to live streams.

## Alternatives considered

Have you looked at how this could be done another way, and why is this the better approach?

## Additional context

Links, mock-ups, or related issues that help the maintainers understand the request.