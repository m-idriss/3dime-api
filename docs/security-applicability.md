# Release applicability review

The owner authorized production deployment on 2026-10-28. Production must still
pass the existing tests and security thresholds. This release combines the NVD
cache changes from PR #270 and the Jackson/Guava/Brotli fixes from PR #271.
It does not include the Stripe update, provider-output changes, or unqualified
platform/telemetry updates. ADR 0001 still applies to the pinned Firestore
instrumentation API.

## Evidence behind narrow, expiring exclusions

The Eclipse CNA record for
[CVE-2026-102469](https://github.com/CVEProject/cvelistV5/blob/main/cves/2026/102xxx/CVE-2026-102469.json)
identifies `io.vertx:vertx-db2-client`: DRDA UDT data deserialization through
`ObjectInputStream`. The initial matches were on `vertx-web` and `vertx-grpc-server` 4.5.34;
a freshly updated database also matches `vertx-core` and `vertx-web-client`
4.5.34 with the same DB2-only advisory. An artifact/product CPE association does not establish
that the DB2 implementation is present. The exclusion matches only those four
exact package URLs and that single CVE.

The Eclipse CNA records for
[CVE-2017-7657](https://github.com/CVEProject/cvelistV5/blob/main/cves/2017/7xxx/CVE-2017-7657.json)
and
[CVE-2017-7658](https://github.com/CVEProject/cvelistV5/blob/main/cves/2017/7xxx/CVE-2017-7658.json)
describe Jetty Server HTTP request smuggling. The Maven Central NPN API artifact
`org.eclipse.jetty.npn:npn-api:8.1.2.v20120308` contains exactly four
`org/eclipse/jetty/npn/NextProtoNego` API classes and no HTTP/server parser.
Its SHA1 is `d8e347c5b0ee0bf38a44789465ef28d01ac12219`. The exclusion requires
that exact artifact hash and those two CVEs, not every Jetty advisory.

Both exclusions expire on 2026-12-31. The CVSS 9.0 release threshold is unchanged.
New artifact versions and new advisories require fresh review.

## Independent package boundary

Before Dependency-Check runs in `mvn -Psecurity-scan verify`, the profile invokes
`scripts/verify-security-applicability.py` after packaging. Python 3 is required
for this security profile. It requires a recognizable Vert.x uber-jar, inspects
the runner and every additional `target/quarkus/**/*.jar`, and rejects any DB2
client or Jetty HTTP/server implementation class. Missing/invalid packages,
nested archives and inspection bounds fail closed. It reads archive entry names
without extracting application contents or logging customer/provider data.

Four regression tests cover the NPN API distinction, each affected namespace,
additional runtime jars, nested archives and missing/unrecognized packages.
The security workflow also prints the filtered Maven dependency origins before
analysis. A green exclusion file alone is insufficient: the runtime boundary,
fresh NVD scan, image vulnerability gate, API tests, unchanged contract and
package checks must all pass.

No threshold reduction, global CVE exclusion or analyzer disabling is introduced.
The container scanner now explicitly returns exit code 1 for its configured
fixable high/critical policy. Existing scanner coverage limits still apply.

## Release verification

The isolated warm qualification checks out and asserts the exact reviewed
release SHA while restoring the existing trusted branch-scoped public NVD
cache. It forces an API refresh before analysis; no stale-data fallback is
allowed. The runtime package guard was successfully verified in the hosted
release analysis. The temporary cold qualification push trigger is removed
before integration; normal weekly/manual scans retain their bounds.

After the authorized main merge, `verify-production-release.yml` waits for the
Cloud Build deployment check on that exact commit, then performs bounded public
GETs of API health and frontend availability. It has checks:read only, no
checkout or production/provider credentials, a 23-minute job limit and no
customer/provider operation. A missing, failed or timed-out deployment check
cannot be reported as a successful release.
