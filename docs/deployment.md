# Deployment

## Overview

Deployment uses an explicit **`Dockerfile`** with Cloud Build via Google Cloud Run. The `Dockerfile` defines a two-stage build for optimal image size and build caching.

## Google Cloud Build + Cloud Run

The app is deployed via **Cloud Build trigger** (configured in GCP Console) which:

1. Detects pushes to the main repository
2. Runs the Cloud Build pipeline defined in `cloudbuild.yaml`
3. Builds the Docker image using explicit Docker builder (not buildpacks)
4. Pushes the image to Google Container Registry (GCR)
5. Deploys the latest image to Cloud Run

### Cloud Build Trigger Configuration (GCP Console)

**⚠️ Important**: The Cloud Build trigger must be configured to use the **Dockerfile** build method, not "Auto-detected" (which defaults to buildpacks for Java projects).

1. Go to **Cloud Build** → **Triggers** → **3dime-api** (or create new)
2. Edit the trigger and set:
   - **Build configuration**: Dockerfile
   - **Dockerfile location**: `./Dockerfile`
   - **Machine type**: n1-highcpu-8 (or suitable for build time)
3. Save

The `cloudbuild.yaml` file specifies the exact build steps and machine type.

### Docker Image Build Process

The `Dockerfile` uses a two-stage build:

**Stage 1 (Builder)**:
- Base image: `maven:3.9-eclipse-temurin-21`
- Copies `pom.xml` and `src/`
- Runs `mvn clean package -DskipTests`
- Outputs: `target/3dime-api-runner.jar` (uber-jar) and `target/quarkus/` (bootstrap files)

**Stage 2 (Runtime)**:
- Base image: `eclipse-temurin:21-jre-alpine` (~27 MB base)
- Copies only the compiled jar and bootstrap files
- Exposes port 8080
- Health check: `/health` endpoint
- Entrypoint: `java -Dquarkus.http.host=0.0.0.0 -Dquarkus.http.port=8080 -jar 3dime-api-runner.jar`

### Local Docker Build (for Testing)

```bash
# Build image
docker build -t 3dime-api:latest .

# Run container with environment variables
docker run -p 8080:8080 \
  -e NOTION_TOKEN=secret_xxx \
  -e NOTION_TRACKING_DB_ID=db_xxx \
  -e GEMINI_API_KEY='{"type":"service_account",...}' \
  -e ADMIN_PASSWORD=changeme \
  -e AUTH_SESSION_ENCRYPTION_KEY=at-least-16-chars \
  3dime-api:latest

# Test health endpoint
curl http://localhost:8080/health
```

### Deploy via gcloud CLI (Alternative to Cloud Build Trigger)

If not using Cloud Build trigger, deploy directly:

```bash
gcloud run deploy 3dime-api \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars "NOTION_TOKEN=secret_xxx,NOTION_TRACKING_DB_ID=db_xxx"
```

However, this requires `gcloud` to handle the build, which may revert to buildpacks. The recommended approach is using the Cloud Build trigger.

---

## Packaging

The app builds as an **uber-jar** (`quarkus.package.jar.type=uber-jar`). The final artifact is `target/3dime-api-runner.jar`.

**Local build** (without Docker):
```bash
mvn clean package -DskipTests
java -jar target/3dime-api-runner.jar
```

The Docker build in `Dockerfile` automates this and adds:
- Multi-stage caching for faster builds
- Minimal runtime image using Alpine JRE (27 MB base)
- Health check configuration
- Labels and metadata for container registry

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
