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

A temporary push trigger is restricted to security/prepare-nvd-credential and changes to this workflow or this qualification document. Push qualification runs only the NVD/Maven job; the previously qualified container scan remains unchanged for weekly/manual execution. No owner dispatch click is needed. Remove this temporary trigger after cold and warm verification.

Verify a cold cache miss, completed official-feed update, successful cache save and a completed dependency analysis/report. Then update this document with the actual result to launch a second run; verify cache restoration, cold-bootstrap skip, completed incremental API refresh and completed analysis. A vulnerability-threshold failure is a security finding, not a technical freshness pass or reason to weaken the policy. Report both dimensions separately.

## Current state

Implementation and static YAML/shell/pin/credential-scope checks passed. The first workflow dispatch-by-push was rejected before execution because runner context is unavailable in job-level environment expressions; the cache path is now a fixed runner-temporary path. Local actionlint 1.7.7 verification of both touched workflows passed after correction. Live cold/warm database qualification is pending. Official feed probe: https://github.com/m-idriss/3dime-api/actions/runs/40672690166 . Credential setup and initial interruption: https://github.com/m-idriss/photocalia-agents/issues/13 . Tested dependency patch remains isolated in draft backend PR #271.

## Cold execution verified

[Run 40673490915](https://github.com/m-idriss/3dime-api/actions/runs/40673490915), source `2ea9ac10dd225f08ce74aa0d646473677a8825cb`, completed the official-feed update in 6 min 36 s. The checkpoint measured 263,843,840 database bytes; cache save confirmed key `nvd-public-db-13.0.0-Linux-X64-40673490915-1`. Analysis completed in 13 seconds and produced the dependency report artifact 12810036255. The workflow failed because dependencies crossed the retained CVSS threshold; no passing security release is claimed. A certificate-related analyzer exception also appears in the log and needs separate review. Cache/analysis completion and vulnerability clearance are distinct results.

The exact v13.0.0 update-only goal and engine were inspected: update exceptions propagate and failOnError defaults to true. No fallback to stale cached data qualifies a failed update. The next execution must restore this cache, skip bulk bootstrap, complete the forced incremental API refresh and produce an analysis report.
