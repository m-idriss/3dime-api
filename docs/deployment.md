# Deployment

## Google Cloud Run (Buildpacks)

No Dockerfile needed -- the app uses [Google Cloud Buildpacks](https://buildpacks.io/).

The buildpack automatically detects Java 21 from `pom.xml`, builds with Maven, and creates an optimized container.

### Basic Deploy

```bash
gcloud run deploy 3dime-api \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated
```

### Deploy with Secrets (Recommended)

```bash
gcloud run deploy 3dime-api \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --set-secrets "NOTION_TOKEN=notion-token:latest,GEMINI_API_KEY=gemini-key:latest,ADMIN_PASSWORD=admin-password:latest,AUTH_SESSION_ENCRYPTION_KEY=session-key:latest"
```

---

## Packaging

The app builds as an **uber-jar** (`quarkus.package.jar.type=uber-jar`). The final artifact is `target/3dime-api-runner.jar`.

`project.toml` configures buildpack behavior:
- Java 21 JVM
- Build: `mvn clean package -DskipTests`
- Artifact: `target/3dime-api-runner.jar`

---

## Observability

### Logging

- **Dev**: Console text format with timestamps
- **Prod**: JSON structured logs (`quarkus-logging-json`). Key remapping: `level` -> `severity`, `timestamp` -> `time`. Additional fields: `service=3dime-api`, `version=1.0.0`

### Tracing

- Provider: OpenTelemetry
- Exporter: OTLP gRPC -> `https://otel.googleapis.com:4317` (Google Cloud Trace)
- Disabled in dev (`%dev.quarkus.otel.traces.enabled=false`)
- Service name attribute: `service.name=3dime-api`
