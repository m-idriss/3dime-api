# CI and Packaging

## Pull Request Checks

`API CI / Java 21 tests and package` runs for pull requests targeting `main` when API source,
Maven, or workflow files change.

The job uses Java 21 and runs:

```bash
mvn -B -ntp clean verify
```

It then rebuilds the application package and compares the sorted entry manifest for
`target/3dime-api-runner.jar`:

```bash
mvn -B -ntp clean package -DskipTests
```

The package comparison protects against accidental packaging drift while preserving the existing
Quarkus uber-JAR deployment contract. Test reports from `target/surefire-reports` and
`target/failsafe-reports` are uploaded on every run. Failure logs and Maven dump files are uploaded
when a check fails.

## Test Profile Isolation

The `%test` profile disables startup cache warmups, OpenTelemetry exporters, and live REST-client
base URLs so CI tests do not require Firestore, Notion, Stripe, GitHub, Gemini, Claude, or Google
Cloud Trace credentials. Maven test runs also set `dotenv.enabled=false` so local `.env` secrets do
not leak into the test profile. Tests that exercise integrations must mock clients or service fields
directly.

## Scheduled Security Scans

`Security Scan` runs weekly on Mondays at 04:24 UTC and can also be started manually.

It publishes:

- OWASP Dependency-Check HTML, JSON, and SARIF reports.
- Trivy container SARIF reports for the Docker image built from the current repository state.

Dependency-Check is intentionally isolated behind the `security-scan` Maven profile so vulnerability
database availability does not make normal PR builds non-deterministic.

## Ownership and Triage

The maintainer on rotation owns weekly scan review.

1. Review GitHub code scanning alerts and workflow artifacts after each scheduled run.
2. Open or update one issue per actionable dependency or container finding.
3. Label each issue with `security` plus the affected area, such as `java`, `config`, or `docker`.
4. Mark the issue priority from exploitability, reachability, and available fixes.
5. Close the issue only after the fix merges and the next scan no longer reports the finding.

## Required Branch Protection

Configure `main` to require this status check before merge:

```text
API CI / Java 21 tests and package
```

Keep `Security Scan` out of required pull-request checks because it is scheduled reporting and may
depend on external vulnerability feeds.
