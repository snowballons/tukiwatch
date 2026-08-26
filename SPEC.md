# SPEC: Deploy Hosted TukiWatch API on Railway with Redis-Backed Rate Limiting

**Status:** DRAFT
**Issue:** SNOW-2 (01a03dc6-0ef6-7bcc-9ef0-df62ecd8ba3e)
**Author:** Architect Agent

---

## 1. Problem Statement

The TukiWatch API currently runs as a self-hostable FastAPI backend with in-memory rate limiting. We need to deploy a managed public endpoint on Railway so users who prefer not to self-host can use a hosted API. The critical missing piece is **Redis-backed rate limiting with in-memory fallback** to enable horizontal scaling on Railway while maintaining local development without Redis.

---

## 2. Success Criteria

- [ ] API deploys to Railway via `nixpacks.toml` (already exists)
- [ ] Rate limiting uses Redis-backed sliding window when `REDIS_URL` is configured
- [ ] Rate limiting gracefully falls back to in-memory when Redis is unavailable
- [ ] Rate limiting works correctly across multiple Railway instances (shared state)
- [ ] Environment variables documented for Railway deployment
- [ ] Mobile app connection documentation updated for hosted endpoint
- [ ] All quality gates pass (ruff, pytest)

---

## 3. Functional Requirements

### 3.1 Redis-Backed Rate Limiting

| Requirement | Detail |
|-------------|--------|
| **Algorithm** | Sliding window with Redis sorted sets (ZSET) for precision |
| **Key structure** | `ratelimit:{ip}:{endpoint}` → ZSET with timestamps as scores |
| **Window** | Per-endpoint configurable (matches existing `RateLimitConfig`) |
| **Atomic operations** | Lua script for check-and-increment to avoid race conditions |
| **TTL** | Auto-expire keys after `time_window + buffer` seconds |
| **Fallback** | Seamless fallback to existing `CustomRateLimitMiddleware` in-memory logic |

### 3.2 Endpoint Rate Limits (existing, preserved)

| Endpoint | Limit | Window |
|----------|-------|--------|
| `/resolve` | 20 req/min | 60s |
| `/status-batch` | 10 req/min | 60s |
| `/health` | 200 req/min | 60s |
| `/cache/stats` | 50 req/min | 60s |
| Default | 100 req/min | 60s |

### 3.3 Environment Variables

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| `REDIS_URL` | Redis connection string (e.g., `redis://:pass@host:port/db`) | No | In-memory fallback |
| `ALLOWED_ORIGINS` | Comma-separated CORS origins | No | `*` |
| `TWITCH_OAUTH_TOKEN` | Twitch Turbo OAuth for ad-free streams | No | — |
| `PORT` | Provided by Railway automatically | Yes (Railway) | — |

---

## 4. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| **Performance** | Rate limit check < 5ms p99 (Redis local), < 20ms p99 (cross-AZ) |
| **Reliability** | Redis failure → in-memory fallback within same request (no 500) |
| **Scalability** | Horizontal scaling on Railway (multiple replicas share Redis state) |
| **Observability** | `/rate-limit/stats` shows backend type (Redis vs in-memory) |
| **Security** | Rate limit headers (`X-RateLimit-*`) exposed; `Retry-After` on 429 |
| **Compatibility** | Zero breaking changes to existing API contracts |

---

## 5. Data Models

### 5.1 Redis Key Schema

```
Key: ratelimit:{client_ip}:{normalized_endpoint}
Type: Sorted Set (ZSET)
Score: Unix timestamp (milliseconds precision)
Member: UUID (unique per request for counting)
TTL: time_window + 10 seconds (auto-cleanup)
```

### 5.2 Rate Limit Response Headers

```
X-RateLimit-Limit: <max_requests>
X-RateLimit-Remaining: <remaining>
X-RateLimit-Reset: <unix_timestamp>
Retry-After: <seconds> (on 429 only)
```

---

## 6. API Contracts

No changes to existing REST endpoints. The rate limiting behavior is internal.

### 6.1 Error Response (429 - unchanged)

```json
{
  "error": "Rate limit exceeded",
  "message": "Too many requests. Please try again later.",
  "retry_after": 45,
  "type": "rate_limit_error"
}
```

### 6.2 Enhanced `/rate-limit/stats` Response

```json
{
  "rate_limits": { ...existing... },
  "backend": "redis" | "memory",
  "redis_connected": true | false,
  "service": "streamlink-api"
}
```

---

## 7. Architecture Decision Records

### ADR-001: Sliding Window via Redis Sorted Sets

- **Status:** Accepted
- **Context:** Need distributed rate limiting for Railway horizontal scaling
- **Decision:** Use Redis ZSET with timestamps as scores, Lua script for atomicity
- **Alternatives:** 
  - Fixed window (simpler but bursty at boundaries) → Rejected
  - Token bucket (complex, overkill) → Rejected
  - External service (e.g., Cloudflare) → Rejected (vendor lock-in)
- **Consequences:** Requires Redis dependency; adds ~2ms latency per request

### ADR-002: In-Memory Fallback Strategy

- **Status:** Accepted
- **Context:** Local dev and Railway without Redis plugin must work
- **Decision:** Same `CustomRateLimitMiddleware` class detects Redis availability at startup; wraps both implementations
- **Alternatives:**
  - Separate middleware classes → Rejected (duplication)
  - Always require Redis → Rejected (breaks local dev)
- **Consequences:** Slight code complexity; dual code paths to test

---

## 8. Module Boundaries & Ownership

| Module | Responsibility | Owner |
|--------|----------------|-------|
| `app/rate_limit.py` | Rate limit configuration (limits per endpoint) | Backend |
| `app/middleware.py` | HTTP middleware, IP extraction, response headers | Backend |
| `app/rate_limiter.py` (NEW) | Redis/in-memory rate limiter implementations | Backend |
| `config.py` | Environment variable parsing | Backend |
| `nixpacks.toml` | Railway build/start config | DevOps |
| `docs/SELF_HOSTING.md` | Deployment & client connection docs | Docs |

---

## 9. Observability Targets

| Metric | Target |
|--------|--------|
| Rate limit check latency (Redis) | p50 < 2ms, p99 < 10ms |
| Rate limit check latency (Memory) | p50 < 0.5ms, p99 < 2ms |
| Redis connection failure rate | < 0.1% |
| Fallback activation rate | 0% in production (Redis always available) |

---

## 10. Rollback Strategy

1. **Code rollback:** Revert to commit before Redis rate limiter (single PR revert)
2. **Config rollback:** Unset `REDIS_URL` → auto-fallback to in-memory
3. **Data rollback:** Rate limit keys are ephemeral (TTL-based); no persistent data to migrate

---

## 11. Testing Requirements

| Test Type | Coverage |
|-----------|----------|
| Unit | `RateLimiter` Redis + memory implementations |
| Integration | Middleware with real Redis (testcontainers) |
| E2E | Railway deployment with multiple replicas |
| Load | 1000 req/s burst → verify 429 behavior |

---

## 12. Contracts

- **OpenAPI:** No changes (rate limiting is transparent)
- **Protobuf:** N/A
- **Event Schemas:** N/A