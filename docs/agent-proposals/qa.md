# QA — reviewed backend coverage proposals

## Verified baseline

- [API qualification 40703939841](https://github.com/m-idriss/3dime-api/actions/runs/40703939841) passed 150 tests with no failures, errors or skips on the reviewed release tree. Existing tests cover real Firestore client/tracing construction using controlled fixtures.
- API CI checks the generated OpenAPI contract and deterministic packaging. This is not a claim that every HTTP/provider interaction is covered.
- The POM skipITs setting alone cannot establish whether integration/compatibility checks run in CI.

## Proposals requiring investigation

1. Read test sources and CI commands, distinguish test categories and document specific untested boundaries. Do not merely toggle skipITs or assert that integration testing is absent.
2. If a new integration test is justified, use synthetic data and isolated/emulated dependencies, with no production Firestore access or real provider/customer requests.
3. Keep PR #256 separate until the required controlled provider corpus has been qualified; documentation merge does not approve its parser/API behavior.

## Interpretation and execution boundary

Reviewed on October 28, 2026. The original model proposals came from [planning run 40737332878](https://github.com/m-idriss/photocalia-agents/actions/runs/40737332878), which inspected bounded public build descriptors and workflow metadata, not full source or customer metrics. Unsupported absence/coverage assertions have been removed. These documents are planning records, not executable tasks or evidence that the suggested features are missing.

Do not infer a missing control from missing observations. Before implementation, inspect the current source, existing tests and workflow definitions, record a concrete gap and approve a bounded task. These proposals do not expand the automatic code/release catalogue. No payment, pricing, customer-data, authentication, production-provider or outreach operation is authorized by merging this document.
