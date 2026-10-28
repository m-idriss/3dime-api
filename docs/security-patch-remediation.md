# Isolated backend security patch proposal

A credential-qualified isolated scan found six container CVEs, including five high-severity results. Evidence and exact package versions are tracked in https://github.com/m-idriss/photocalia-agents/issues/17 . The original scanner job returned success despite those findings, so job success alone must not be interpreted as a clean image.

This proposal changes dependency management and the image build only:

- Import Jackson BOM 2.22.3 before the platform BOM to align the full Jackson family at the reported patched release.
- Manage Guava at 33.7.2-jre, the reported patched version in the existing release family.
- Upgrade brotli-libs to at least 1.2.1-r0 in the existing Java 21 Alpine runtime family.
- Add an isolated PR-only container check with pinned Actions, read-only permissions, no credentials, a fifteen-minute timeout, no image push and a nonzero high/critical vulnerability exit code. The scanner checks dependencies only; reports expire after three days. Unfixed advisories are excluded by the existing scan policy; the gate is not proof that no vulnerability exists.

The Jackson and Guava artifacts were confirmed available from Maven Central. Existing API CI must verify Java 21 tests, package generation, unchanged OpenAPI and deterministic manifests. The container gate must build and verify the resulting image. These checks do not prove real provider behavior or compatibility for every other consumer of this shared backend.

Keep this PR in draft. Do not merge into main or deploy the backend as part of this proposal. No application source, auth configuration, payment/pricing logic, customer fixture, service data or production resource is changed. The NVD credential-wiring proposal remains separate in backend PR #270; no NVD credential is needed by this PR-only container gate.
