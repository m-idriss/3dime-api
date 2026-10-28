# CTO-002: qualify the next backend dependency batch

## Scope

Qualify Quarkus platform 3.40.1.1 and Firebase Admin 9.11.0 together on the
isolated `agents/cto-backend-platform-refresh` branch, starting from production
commit 28d5993. Existing Dependabot proposals are #269 and #265. This task
prepares a reviewable integration candidate; its qualification workflow does
not merge or deploy it.

The published Quarkus BOM manages Vert.x 4.5.35 and OpenTelemetry API 1.62.0.
It also manages older Jackson/Guava versions than the existing security fixes,
so retain Jackson BOM 2.22.3, Guava 33.7.2-jre and the existing Brotli floor.
Keep gRPC instrumentation 2.23.0-alpha and the executable Firestore channel and
completion compatibility tests required by ADR 0001. Do not include #268's
incompatible standalone instrumentation update, Stripe #266, or provider
behavior proposal #256.

## Release boundary

Remove the temporary CVE-2026-102469 Vert.x exclusion instead of carrying it
into a patched platform release. All resolved Vert.x versions must be inspected
and full fresh NVD analysis must pass without that waiver. Keep the narrowly
identified NPN API exclusions, their expiry and the real-runtime applicability
guard. The regression now explicitly rejects any remaining DB2 advisory or
package-URL exclusion.

Hosted qualification must pass Java 21 tests, including Firestore tracing
construction/completion, unchanged OpenAPI, deterministic package manifests,
fresh NVD analysis at CVSS 9.0, and explicit fixable high/critical image gates.
The isolated CTO branch is added to the existing bounded Security Scan push
allowlist. Its jobs have read-only source access and no production credential;
the existing NVD secret remains scoped only to its guard/incremental updater.
Database freshness, cache boundaries and report retention remain unchanged.

No application source, payment behavior, pricing, customer records or production
configuration is edited. Tests use synthetic credentials/data. No new secret,
paid model call, daily schedule or autonomous merge permission is introduced.
Source-writing model agents remain disabled; this is the first bounded CTO
dependency qualification task, not activation of an unsupervised coding agent.

## Evidence

Qualification results and integration status are tracked in the linked PR and
the private Photocalia agents CTO task report. A failed compatibility, contract
or security gate blocks the proposed batch; it must not be bypassed or hidden
by changing snapshots, suppressing new findings or weakening tests.
