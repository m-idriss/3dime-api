# Bounded NVD bootstrap and cache qualification

## Problem and preserved limits

The first authenticated API import stopped after approximately 35 minutes at 160,000 of 415,637 CVEs (38 percent). The credential itself was qualified with HTTP 200. Repeating a full API import is not an adequate bootstrap strategy.

The scanner remains OWASP Dependency-Check 13.0.0, Java 21, with the existing vulnerability threshold. The job remains capped at 35 minutes. No backend main merge or deployment is authorized.

## Data flow

1. Restore only the scanner's public data directory in the runner's temporary `/tmp/photocalia-nvd-database` directory. The key includes scanner version, runner OS/architecture and a unique run/attempt identifier; the restore prefix selects the latest compatible, branch-scoped cache.
2. If no compatible cache exists, run the pinned plugin's update-only goal against official NVD annual JSON 2.0 feeds. No NVD credential is supplied to this bootstrap step. The exact v13.0.0 implementation supports official metadata fallback and annual/modified feeds. The old/current/modified metadata endpoints were live-qualified with HTTP 200 and digest/freshness fields present.
3. If a compatible cache exists, force an incremental NVD API update using only the configured environment-variable selector. Credential values never appear in arguments or reports.
4. Require successful update, a nonempty database within the configured size bounds, then save the completed public database before application analysis. The cache cannot contain the working tree, Maven settings, environment files, credentials or scan reports. A failed/partial update is never saved by this workflow.
5. Run the existing security profile with auto-update disabled only after the fresh-update prerequisite succeeds. This avoids a second import, not a freshness check. The cache alone is never accepted as evidence of a current vulnerability analysis.

Cold bootstrap has a 20-minute step bound; incremental refresh and analysis each have ten-minute bounds; cache save has five minutes. The 35-minute global deadline is authoritative. All touched Actions are pinned to verified commit SHAs, checkout credentials are not persisted, reports expire after three days, and no source write/deployment permission is added.

## Qualification procedure

Temporary isolated-branch push qualification ran only the NVD/Maven job without another owner click. It has now been removed after both passes; the original weekly/manual event surface and both scan jobs are restored. No backend main merge or production deployment occurred.

Verify a cold cache miss, completed official-feed update, successful cache save and a completed dependency analysis/report. Then update this document with the actual result to launch a second run; verify cache restoration, cold-bootstrap skip, completed incremental API refresh and completed analysis. A vulnerability-threshold failure is a security finding, not a technical freshness pass or reason to weaken the policy. Report both dimensions separately.

## Current state

Implementation and static YAML/shell/pin/credential-scope checks passed. Local actionlint 1.7.7 verification of both touched workflows passed; the data path is fixed outside the working tree. Both live cold/warm database paths are qualified; vulnerability clearance is not. Official feed probe: https://github.com/m-idriss/3dime-api/actions/runs/40672690166 . Credential setup and initial interruption: https://github.com/m-idriss/photocalia-agents/issues/13 . Tested dependency patch remains isolated in draft backend PR #271.

## Cold execution verified

[Run 40673490915](https://github.com/m-idriss/3dime-api/actions/runs/40673490915), source `2ea9ac10dd225f08ce74aa0d646473677a8825cb`, completed the official-feed update in 6 min 36 s. The checkpoint measured 263,843,840 database bytes; cache save confirmed key `nvd-public-db-13.0.0-Linux-X64-40673490915-1`. Analysis completed in 13 seconds and produced the dependency report artifact 12810036255. The workflow failed because dependencies crossed the retained CVSS threshold; no passing security release is claimed. A certificate-related analyzer exception also appears in the log and needs separate review. Cache/analysis completion and vulnerability clearance are distinct results.

The exact v13.0.0 update-only goal and engine were inspected: update exceptions propagate and failOnError defaults to true. No fallback to stale cached data qualifies a failed update. The next execution must restore this cache, skip bulk bootstrap, complete the forced incremental API refresh and produce an analysis report.

## Warm execution verified

[Run 40675177307](https://github.com/m-idriss/3dime-api/actions/runs/40675177307), source `9869e89718c6d82227b30ff646e36e6b044c6adc`, restored the exact cold cache, skipped annual bootstrap and completed the forced API update in 16.489 seconds (engine update 7.014 seconds). Its checkpoint measured 263,774,208 database bytes and saved a new compatible cache. Analysis completed in 14 seconds and the full job took 1 min 33 s, producing report artifact 12810845387 with three-day retention. This confirms actual cache reuse and refreshed analysis, not merely a cache-action success status.

## Security findings remain blocking

The cold report contains 216 dependencies, 55 CVE instances and 34 unique CVE IDs. Four instances across three CVE IDs have reported CVSS >= 9: CVE-2017-7657 and CVE-2017-7658 on npn-api-8.1.2.v20120308.jar; CVE-2026-102469 on vertx-grpc-server and vertx-web 4.5.34. These scanner matches require dependency-origin and affected-product triage, including possible CPE false positives; no suppression or policy weakening is included. Both jobs correctly remain red for findings. Full evidence and the certificate-related analyzer diagnostic are tracked at https://github.com/m-idriss/photocalia-agents/issues/18 . Cache freshness qualification is separate from a clean security release.

Touched workflow validation passed actionlint 1.7.7, YAML parsing, shell syntax and diff checks. Update-only failures propagate in the exact plugin version, whose failOnError default is true. A cache without a successful refresh cannot lead to application analysis. Cache data is public vulnerability material only, scoped by the exact tool version and runner OS/architecture; a future scanner-version change must use a corresponding new namespace. No deployment, runtime application source, payment, price, customer-data change, paid model call or new secret was introduced.

The report-inspection helper is now manual-only too, restricted to the isolated preparation branch. Its run-ID defaults reference the short-lived qualification artifacts; select available run IDs when manually inspecting later evidence. It is not a recurring production workflow. Final warm-report inspection run 40676157141 confirmed complete report parsing without raw messages.
