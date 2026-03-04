# Health Monitoring

The `/health/ready` endpoint checks each external dependency individually and reports structured status with latency.

## Dependency Map

| Dependency | Check | Critical | Failure Impact |
| :--- | :--- | :---: | :--- |
| **Firestore** | Reads `users` collection (500 ms timeout) | Yes | Readiness -> `DOWN` -- Traffic halted on Cloud Run |
| **Gemini API** | Validates OAuth2 service-account token | Yes | Readiness -> `DOWN` -- Traffic halted on Cloud Run |
| **Notion API** | `GET /v1/users/me` (500 ms timeout) | No | Degraded -- readiness stays `UP`, error surfaced in data |
| **GitHub API** | `GET /rate_limit` (500 ms timeout) | No | Degraded -- readiness stays `UP`, error surfaced in data |

## Global Status Logic

```
All critical deps UP  ->  status: UP    (HTTP 200)
Any critical dep DOWN ->  status: DOWN  (HTTP 503) -- Cloud Run stops routing traffic
Non-critical dep DOWN ->  status: UP    (HTTP 200) -- app still serves; error visible in check data
```

## Example Response

```json
GET /health/ready

{
  "status": "UP",
  "checks": [
    { "name": "firestore", "status": "UP",   "data": { "latencyMs": 42 } },
    { "name": "gemini",    "status": "UP",   "data": { "latencyMs": 118 } },
    { "name": "notion",    "status": "UP",   "data": { "latencyMs": 95 } },
    { "name": "github",    "status": "UP",   "data": { "latencyMs": 61 } }
  ]
}
```

## Degraded Example (Notion unreachable)

```json
GET /health/ready

{
  "status": "UP",
  "checks": [
    { "name": "firestore", "status": "UP",   "data": { "latencyMs": 38 } },
    { "name": "gemini",    "status": "UP",   "data": { "latencyMs": 110 } },
    { "name": "notion",    "status": "UP",   "data": { "latencyMs": 501, "status": "DOWN", "error": "timeout" } },
    { "name": "github",    "status": "UP",   "data": { "latencyMs": 74 } }
  ]
}
```

> **Performance** -- Results are cached for **15 seconds** per check to avoid hammering external APIs on every Cloud Run health poll.
