**Yes.** Twitch provides an official API endpoint for this.

### Official Endpoint: Get Streams

- **URL**: `GET https://api.twitch.tv/helix/streams`
- **Docs**: [Twitch API Reference – Get Streams](https://dev.twitch.tv/docs/api/reference#get-streams)

It returns currently live streams, sorted by viewer count (descending). Results are paginated (max 100 per page).

#### Authentication (required)

1. Register an application in the https://dev.twitch.tv/console to get a **Client ID** and **Client Secret**.
2. Obtain an **App Access Token** (Client Credentials flow) — this is the simplest option for public stream data (no user login needed):

```bash
curl -X POST 'https://id.twitch.tv/oauth2/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=YOUR_CLIENT_ID&client_secret=YOUR_CLIENT_SECRET&grant_type=client_credentials'
```

Use the returned `access_token` in requests.

#### Example Request

```bash
curl -X GET 'https://api.twitch.tv/helix/streams?first=20' \
  -H 'Authorization: Bearer YOUR_APP_ACCESS_TOKEN' \
  -H 'Client-Id: YOUR_CLIENT_ID'
```

#### Useful Query Parameters

| Parameter                | Description                    | Notes                   |
| ------------------------ | ------------------------------ | ----------------------- |
| `first`                  | Number of streams to return    | Max 100, default 20     |
| `after`                  | Cursor for next page           | From previous response  |
| `game_id`                | Filter by game/category ID(s)  | Up to 100               |
| `user_id` / `user_login` | Filter by specific streamer(s) | Up to 100               |
| `language`               | Filter by language (e.g. `en`) | Up to 100               |
| `type`                   | `live` (or `all`)              | Usually just use `live` |

#### Response (simplified)

```json
{
  "data": [
    {
      "id": "stream_id",
      "user_id": "123456",
      "user_login": "streamername",
      "user_name": "StreamerName",
      "game_id": "509658",
      "game_name": "Just Chatting",
      "type": "live",
      "title": "Stream title here",
      "viewer_count": 12500,
      "started_at": "2026-09-04T06:00:00Z",
      "language": "en",
      "thumbnail_url": "https://static-cdn.jtvnw.net/previews-ttv/live_user_...-{width}x{height}.jpg",
      "tag_ids": [...],
      "is_mature": false
    }
  ],
  "pagination": {
    "cursor": "eyJiIjpudWxsLCJhIjp7Ik9mZnNldCI6MjB9fQ=="
  }
}
```

### Important Notes for Apps

- **Rate limits**: Be respectful; Twitch has rate limits. Cache results when possible.
- **Pagination**: Use the `pagination.cursor` with the `after` parameter for more results. Note that the list can change between pages as viewers join/leave.
- **Checking specific channels**: Pass `user_login=channelname` (or `user_id`). An empty `data` array means the channel is offline.
- **Real-time updates**: This is a polling endpoint. For push-style updates (e.g., when a specific streamer goes live), use **EventSub** (Twitch’s webhook/WebSocket system).
- **Followed streams**: There’s also `GET /helix/streams/followed` (requires a user access token with the appropriate scope).

### Libraries / SDKs

Popular wrappers exist for most languages (e.g., TwitchLib for .NET, twitchAPI for Python, various JS clients). Search for “Twitch Helix API” + your language.

That’s the official and recommended way. Third-party services exist, but using the official Helix API is the proper approach for an app.

**Yes — Twitch Helix is still the best (and recommended) way** for a local-first app that wants to discover currently live streams.

### Why Helix is the right choice

- It is the **official, supported, documented** API.
- Free (no paid tier required).
- Returns structured, reliable data (title, viewer count, game, language, thumbnail, start time, etc.).
- Works well with App Access Tokens (no user login needed for public stream discovery).
- Supports filtering (game, language, specific users) and pagination.
- Your app can stay fully local-first: store the Client ID/Secret securely, cache results on disk, and only hit the network when the user wants fresh discovery data.

For a discovery feature you will mostly use:

- `GET https://api.twitch.tv/helix/streams` (top live streams + filters)
- Optionally `GET /helix/games/top` or Search Categories for game-based browsing
- EventSub (`stream.online` / `stream.offline`) if you later want push notifications for specific channels (requires a public HTTPS endpoint or WebSocket)

### Rate limits explained (current behavior)

Twitch uses a **token-bucket** system:

- Default bucket size is **~800 points per minute** per Client ID (for App Access Tokens).
- Most endpoints (including Get Streams) cost **1 point** per request.
- Headers returned with every response:
  - `Ratelimit-Limit`
  - `Ratelimit-Remaining`
  - `Ratelimit-Reset` (Unix timestamp when the bucket refills)
- Hitting the limit → HTTP 429. Just back off until the reset time.

In practice this is very generous for a local-first discovery feature:

- Polling the top 100 streams every 1–2 minutes is trivial.
- Even aggressive multi-page pagination is hard to exhaust on a single Client ID unless you are doing something extreme (thousands of requests per minute).

**Best practices for your app**:

- Cache responses locally (e.g. 30–60 seconds for the same query).
- Respect the `Ratelimit-Remaining` header and add simple exponential backoff on 429.
- Prefer larger `first=100` pages instead of many small ones.
- One Client ID is shared across all users of your app, so design accordingly if you ever scale beyond personal use.

### Strong open-source alternatives?

There are **no strong, production-ready open-source alternatives** that replace Helix for reliable live-stream discovery.

| Approach                                                                | Status                          | Pros                                         | Cons                                                | Recommendation                            |
| ----------------------------------------------------------------------- | ------------------------------- | -------------------------------------------- | --------------------------------------------------- | ----------------------------------------- |
| **Official Helix**                                                      | Official                        | Stable, free, documented, rate-limit headers | Requires Client ID + token                          | **Best choice**                           |
| Twitch GraphQL (gql.twitch.tv)                                          | Unofficial / reverse-engineered | No OAuth needed, richer frontend data        | Can break anytime, ToS risk, no guarantees          | Only for experiments                      |
| Community scrapers / CLI tools (e.g. some GitHub projects that hit GQL) | Hobby                           | Sometimes no auth                            | Fragile, rate-limit unknown, legal gray area        | Avoid for a real app                      |
| Third-party paid APIs (StreamsCharts, etc.)                             | Commercial                      | Cross-platform, historical data              | Cost money, not open-source                         | Only if you need multi-platform analytics |
| Self-hosted streaming platforms (Owncast, PeerTube, etc.)               | Open-source                     | Full control                                 | Completely different product — not Twitch discovery | Irrelevant here                           |

**Bottom line**:  
For a local-first Twitch discovery feature, stick with Helix. It is free, stable, well-documented, and the rate limits are not a practical problem for normal discovery use. Everything else is either unofficial (and fragile) or solves a different problem.

If you later want real-time “this specific channel just went live” notifications without polling, add EventSub on top of Helix — that is still the official path.
