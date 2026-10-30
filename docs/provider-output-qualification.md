# Provider-output normalization qualification

PR #256 remains a draft. Synchronizing it with current main and adding parser
regressions does not authorize production rollout or mark epic #214 complete.

## Audit corrections

- Parse timed values using strict proleptic-year resolution. SMART resolution
  silently adjusted an impossible date such as 2026-02-30 to February 28.
- Reject local timestamps in daylight-saving gaps instead of allowing Java's
  zone conversion to silently move the intended appointment.
- Preserve valid leap days and explicit UTC instants across DST boundaries.
- Validate the supported recurrence fields, ranges and mutually exclusive
  COUNT/UNTIL combination instead of accepting any FREQ prefix.
- Emit a UTC DTSTAMP for new output and preserve validated stamps on replay.
- Accept quoted zone identifiers and warn about ambiguous overlap timestamps.
- Use English synthetic labels and Unicode escape sequences in new tests.

The targeted pure-Java harness compiles actual normalizer/model/ICS utility and
business exception sources, with only the CDI annotation stubbed. It does not
exercise Quarkus injection, HTTP, provider clients, quota storage or production.
Hosted API CI must separately run the complete Java 21 suite and OpenAPI drift
check on the final PR head. No paid provider request or customer data is needed
for those checks.

## Remaining release gates

The live Gemini/Claude corpus requested by #214 and photocalia#916 has not been
run. No provider credential or GCP identity is available to this task, and the
OpenAI agent key is not a Gemini or Claude credential. Mocked responses and
handwritten ICS are not a substitute for measured provider extraction accuracy.
Use synthetic, licensed inputs and explicit expected event models for English
and localized text, all-day/multiple events, DST, missing and ambiguous fields.
Record exact model/version, bounded timeout, no retries/fallback, request count
and usage without logging document content or credentials.

Four handwritten synthetic outputs were parsed independently with the existing
ical.js dependency, asserting required event properties and selected UTC,
all-day and recurrence semantics. This is not a live provider accuracy test or
a complete RFC 5545 conformance proof. The supported RRULE subset rejects
unknown extensions. Timed UNTIL must be UTC; all-day UNTIL must be a date.
Local overlap times retain the zone and emit an ambiguity warning. Timezone
component interoperability across importing clients still requires golden
corpus qualification, including recurrence across DST. Do not infer complete
RFC support or extraction accuracy from syntax parsing.

Do not enable the normalizer in production, weaken tests, call a production
conversion endpoint or use real customer documents to clear these gates.
