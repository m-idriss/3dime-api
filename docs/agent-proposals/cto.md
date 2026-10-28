# CTO — reviewed backend engineering proposals

## Verified baseline

- Release `74e4a2ad6474884a9090ad69f5a0dcbf2247cfe9` contains the qualified Quarkus 3.40.1.1 / Firebase Admin 9.11.0 update.
- [API qualification 40703939841](https://github.com/m-idriss/3dime-api/actions/runs/40703939841) passed 150 tests, including Firestore construction/tracing compatibility checks, before the identical reviewed tree was merged.
- [Main security scan 40705137405](https://github.com/m-idriss/3dime-api/actions/runs/40705137405) passed both Maven and container gates. A POM skipITs default is not proof that compatibility or integration coverage is absent.

## Proposals requiring investigation

1. Inventory current Surefire/Quarkus and any Failsafe test execution. Identify a concrete uncovered boundary before adding a synthetic integration scenario; preserve existing compatibility guards.
2. Treat OpenTelemetry PR #268 as a coordinated compatibility migration, not an isolated version bump. Its known Firestore API incompatibility must be resolved and tested without weakening checks.

## Interpretation and execution boundary

Reviewed on October 28, 2026. The original model proposals came from [planning run 40737332878](https://github.com/m-idriss/photocalia-agents/actions/runs/40737332878), which inspected bounded public build descriptors and workflow metadata, not full source or customer metrics. Unsupported absence/coverage assertions have been removed. These documents are planning records, not executable tasks or evidence that the suggested features are missing.

Do not infer a missing control from missing observations. Before implementation, inspect the current source, existing tests and workflow definitions, record a concrete gap and approve a bounded task. These proposals do not expand the automatic code/release catalogue. No payment, pricing, customer-data, authentication, production-provider or outreach operation is authorized by merging this document.
