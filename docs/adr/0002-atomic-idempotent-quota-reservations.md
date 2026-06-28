# ADR 0002: Atomic Idempotent Quota Reservations

## Status

Accepted

## Context

The conversion endpoint previously checked quota before the AI provider call and incremented usage
after success. Concurrent requests at the final plan boundary could pass the read check together,
and retries could duplicate provider spend or quota usage.

The quota datastore is Firestore, and the API runs on Cloud Run where multiple instances may process
requests for the same user at the same time.

## Decision

Quota-consuming conversion requests must include an `Idempotency-Key` header. Validation happens
before quota reservation. After validation, the API reserves one quota unit in a Firestore
transaction before calling the AI provider.

The persisted model is:

- `users/{userId}`: existing monthly aggregate quota document.
- `users/{userId}/quotaReservations/{idempotencyKey}`: per-request ledger document.

Reservation states are:

- `RESERVED`: quota unit is held before provider work starts.
- `COMPLETED`: provider work succeeded and the response was recorded for idempotent replay.
- `FAILED`: provider work failed and the quota unit was intentionally retained.
- `REFUNDED`: provider work failed and the quota unit was returned.
- `EXPIRED`: reconciliation found an abandoned reservation and returned its quota unit.

The current refund policy is conservative for users: provider and processing failures are refunded.
Validation failures occur before reservation and therefore do not consume quota.

Firestore outages fail closed. If a reservation, completion, refund, or reconciliation write cannot
be made, the API returns a datastore-unavailable error instead of silently granting conversions.

## Migration

Existing `users/{userId}` quota counters remain the source of truth for current monthly usage. The
new reservation subcollection is additive. Users without reservation documents continue from their
existing aggregate `quotaUsed` values, and new conversion requests create ledger entries going
forward.

## Consequences

- Concurrent requests at the plan boundary are serialized by Firestore transactions.
- Replaying the same `Idempotency-Key` does not create a second chargeable conversion.
- Completed responses can be replayed without another provider call.
- Abandoned reservations are eligible for expiry reconciliation after `quota.reservation.ttl-minutes`.
- The reservation ledger stores generated ICS output for replay but never stores submitted image
  bytes or URLs.
