# Provider-output normalization qualification

PR #256 remains a draft. Synchronizing it with current main and adding parser
regressions does not authorize production rollout or mark epic #214 complete.

## Audit corrections

- Parse timed values using strict proleptic-year resolution. SMART resolution
  silently adjusted an impossible date such as 2026-02-30 to February 28.
- Reject local timestamps in daylight-saving gaps instead of allowing Java's
  zone conversion to silently move the intended appointment.
- Preserve valid leap days and explicit UTC instants across DST boundaries.
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

Further parser review is also required before rollout: recurrence validation is
currently only a shallow FREQ prefix check; generated calendars lack DTSTAMP;
ambiguous overlap timestamps and quoted TZID parameters need explicit policy.
An independent RFC 5545 parser must validate golden outputs. These are open
qualification gaps, not claims of RFC-complete validation.

Do not enable the normalizer in production, weaken tests, call a production
conversion endpoint or use real customer documents to clear these gates.
