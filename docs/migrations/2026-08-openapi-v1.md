# OpenAPI v1 migration — August 2026

## Quota response

`GET /v1/converter/quota-status` now returns the canonical public envelope:

```json
{
  "success": true,
  "enabled": true,
  "quota": {
    "usageCount": 1,
    "limit": 3,
    "remaining": 2,
    "plan": "FREE"
  }
}
```

The former raw `UserQuota` response exposed persistence and Stripe fields and is no longer the
public contract. Photocalia accepts both shapes behind the dated
`ENABLE_LEGACY_QUOTA_RESPONSE_UNTIL_2026_10_01` bridge. Remove that bridge after 1 October 2026.

## Errors and correlation

Errors use `ErrorResponse.errorCode` for behavior and `ErrorResponse.requestId` for support
correlation. Human-readable `message` values are not stable API identifiers. Every HTTP response
also returns the same value in `X-Request-ID`.

## Reproducible schema

Run `scripts/update-openapi-contract.sh` after changing public resources or DTOs. Commit
`contracts/openapi-v1.json` with the source change. Backend CI regenerates and compares the file;
Photocalia then runs `npm run contracts:update` to update its snapshot and generated TypeScript.
