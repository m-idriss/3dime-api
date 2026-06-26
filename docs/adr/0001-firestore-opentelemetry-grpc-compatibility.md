# ADR 0001: Firestore and OpenTelemetry gRPC Compatibility

## Status

Accepted

## Context

`google-cloud-firestore` 3.38.0 constructs its traced gRPC client channel through
`io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry.newClientInterceptor()`.

Quarkus 3.36.2 manages newer OpenTelemetry instrumentation artifacts where
`opentelemetry-grpc-1.6` 2.26+ exposes `createClientInterceptor()` instead. Forcing one of those
newer gRPC instrumentation jars onto Firestore's runtime path causes a binary linkage failure:

```text
NoSuchMethodError: GrpcTelemetry.newClientInterceptor()
```

That failure was previously easy to miss because optional cache warmup and Firestore fallback code
caught `Throwable`.

## Decision

Pin `io.opentelemetry.instrumentation:opentelemetry-grpc-1.6` to `2.1.0-alpha`, the version expected
by the Google Cloud dependency set used by Firestore 3.38.0.

Keep Quarkus-managed OpenTelemetry API and instrumentation API versions in place for the rest of the
application. The pinned gRPC instrumentation jar is intentionally older because Firestore's bytecode
calls the older public method name.

Also treat `LinkageError` as unrecoverable. Firestore cache warmup and health checks may absorb
normal runtime exceptions, but binary-incompatibility errors should fail loudly.

## Consequences

- Firestore tracing channel construction is covered by an automated compatibility test.
- A future dependency bump that removes `newClientInterceptor()` fails tests before merge.
- Optional cache warmup failures remain non-blocking for ordinary runtime exceptions.
- Dependency upgrades should revisit this ADR and remove the pin only when Firestore is compiled
  against the newer `createClientInterceptor()` API.
