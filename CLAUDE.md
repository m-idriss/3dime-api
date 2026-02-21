# CLAUDE.md — AI Assistant Guide for 3dime-api

This file provides context for AI assistants (Claude, Copilot, etc.) working on this codebase.

---

## Project Overview

**3dime-api** is a production-ready REST API built with **Java 21** and **Quarkus 3.31.3**, deployed on **Google Cloud Run**. It powers the 3dime.com and photocalia.com frontends.

### Core Features

| Feature | Description |
|---------|-------------|
| Image-to-Calendar | AI-powered conversion of images to `.ics` files via Google Gemini |
| GitHub Integration | Profile, social accounts, and commit statistics from GitHub API |
| Notion CMS | Content management via Notion database integration |
| Quota Management | Per-user rate limiting with `FREE`/`PRO`/`UNLIMITED` plans stored in Firestore |
| Analytics Tracking | Conversion and usage stats persisted to Notion |

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Quarkus 3.31.3 |
| Build | Maven 3.8+ |
| Database | Google Cloud Firestore |
| AI | Google Gemini API (via REST client + service account OAuth2) |
| External APIs | GitHub REST API, Notion API |
| Observability | OpenTelemetry → Google Cloud Trace; JSON structured logging (prod) |
| Resilience | SmallRye Fault Tolerance (`@Retry`, `@Timeout`) |
| Caching | Quarkus Cache (Caffeine backend) |
| Auth | Quarkus Elytron (form-based, embedded users) |
| Documentation | SmallRye OpenAPI / Swagger UI at `/api-docs` |
| Deployment | Google Cloud Run + Buildpacks (`project.toml`) |
| Code Generation | Lombok (`@Data`, `@Slf4j`, `@NoArgsConstructor`, etc.) |

---

## Repository Layout

```
3dime-api/
├── src/
│   ├── main/
│   │   ├── java/com/dime/api/
│   │   │   ├── DimeApplication.java          # JAX-RS Application entry point
│   │   │   └── feature/
│   │   │       ├── converter/                # Image-to-calendar feature
│   │   │       │   ├── ConverterResource.java    # POST /converter, GET /converter/quota-status, /statistics
│   │   │       │   ├── UserQuotaResource.java    # GET|PATCH|DELETE /users/* (admin)
│   │   │       │   ├── GeminiService.java        # Gemini API integration + token caching
│   │   │       │   ├── GeminiClient.java         # MicroProfile REST client interface
│   │   │       │   ├── QuotaService.java         # Firestore-backed quota logic
│   │   │       │   ├── NotionQuotaService.java   # Notion quota sync
│   │   │       │   ├── TrackingService.java      # Usage analytics to Notion
│   │   │       │   ├── ConverterRequest.java     # Request model (userId, files[])
│   │   │       │   ├── ConverterResponse.java    # Response model (success, icsContent)
│   │   │       │   ├── UserQuota.java            # Firestore document model
│   │   │       │   └── PlanType.java             # Enum: FREE | PRO | UNLIMITED
│   │   │       ├── github/                   # GitHub integration feature
│   │   │       │   ├── GitHubResource.java       # GET /github/user, /social, /commits
│   │   │       │   ├── GitHubService.java        # Business logic + @CacheResult
│   │   │       │   ├── GitHubClient.java         # MicroProfile REST client interface
│   │   │       │   └── GitHubUser.java           # Response model
│   │   │       ├── notion/                   # Notion CMS feature
│   │   │       │   ├── NotionResource.java        # GET /notion/cms
│   │   │       │   ├── NotionService.java         # Business logic + @CacheResult
│   │   │       │   └── NotionClient.java          # MicroProfile REST client interface
│   │   │       └── shared/                   # Cross-cutting concerns
│   │   │           ├── RootResource.java          # GET / → redirect to /api-docs
│   │   │           ├── config/
│   │   │           │   ├── DotEnvConfigSource.java  # Loads .env file (ordinal 290)
│   │   │           │   └── JacksonCustomizer.java   # JSON serialization config
│   │   │           ├── exception/
│   │   │           │   ├── BusinessException.java       # Base class for 4xx errors
│   │   │           │   ├── ValidationException.java     # 400 — bad input
│   │   │           │   ├── QuotaException.java          # 429 — quota exceeded
│   │   │           │   ├── ProcessingException.java     # 422 — valid input, bad output
│   │   │           │   ├── ExternalServiceException.java # 502 — upstream failure
│   │   │           │   ├── GlobalExceptionMapper.java   # Catches all Throwable
│   │   │           │   ├── BusinessExceptionMapper.java # Handles BusinessException subclasses
│   │   │           │   ├── ValidationExceptionMapper.java
│   │   │           │   └── ErrorResponse.java           # Standard error envelope
│   │   │           └── health/
│   │   │               ├── LivenessCheck.java     # /health/live
│   │   │               └── ReadinessCheck.java    # /health/ready
│   │   └── resources/
│   │       └── application.properties        # All Quarkus configuration
│   └── test/
│       └── java/com/dime/api/
│           ├── feature/converter/            # Unit + integration tests for converter
│           ├── feature/github/               # Edge case tests for GitHub resource
│           ├── feature/notion/               # Tests for Notion resource
│           └── feature/shared/exception/    # Exception handling tests
├── pom.xml                                   # Maven dependencies and build config
├── project.toml                              # Google Cloud Buildpacks config
├── README.md                                 # User-facing documentation
└── ROADMAP.md                                # Feature roadmap
```

---

## Architecture: Feature-Based Packages

Code is organized **by domain feature**, not by technical layer. Each feature owns its full vertical slice:

```
feature/<name>/
  <Name>Resource.java    — JAX-RS endpoint (HTTP layer)
  <Name>Service.java     — Business logic
  <Name>Client.java      — MicroProfile REST client (external API)
  <Model>.java           — Request/response/domain models
```

When adding a new feature, follow this same pattern. Do not place logic in a generic `service/` or `controller/` package.

---

## Development Commands

```bash
# Start development server with hot reload (Quarkus Dev Mode)
mvn quarkus:dev

# Run unit tests
mvn test

# Run unit + integration tests with coverage
mvn verify

# Build production uber-jar
mvn clean package

# Run production jar
java -jar target/quarkus-app/quarkus-run.jar
```

Dev mode is the primary local workflow. The app will be available at:
- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/api-docs`
- Health: `http://localhost:8080/health`

---

## Testing Conventions

- Framework: **JUnit 5** with **`@QuarkusTest`**
- HTTP testing: **REST-Assured** (`io.restassured`)
- Mocking: **Mockito** (inject mocks by direct field assignment in `@BeforeEach`)
- Test port: random (`%test.quarkus.http.test-port=0`)
- Integration tests test real HTTP via REST-Assured against a running Quarkus test instance
- Unit tests mock Firestore and Notion dependencies directly on the injected service fields
- Use `assertDoesNotThrow` to verify error-resilience (e.g., Firestore exceptions should not propagate to callers)

Example test structure:
```java
@QuarkusTest
class MyServiceTest {
    @Inject MyService service;

    @BeforeEach
    void setup() {
        service.dependency = mock(Dependency.class);
    }

    @Test
    void testHappyPath() { ... }
}
```

---

## Configuration System

Configuration is loaded in this priority order (highest wins):

| Priority | Source | Ordinal |
|----------|--------|---------|
| System properties | JVM `-D` flags | 400 |
| Environment variables | OS env | 300 |
| `.env` file | `DotEnvConfigSource` | 290 |
| `application.properties` | Quarkus default | 100 |

The `.env` file is loaded via `dotenv-java` through `DotEnvConfigSource`. It is **optional** (silently ignored if absent) and intended for local development only. Never commit `.env` to version control.

Quarkus profile prefixes:
- `%dev.*` — only in `mvn quarkus:dev`
- `%prod.*` — only in production jar
- `%test.*` — only during `mvn test`

---

## Environment Variables

### Required for production

| Variable | Description |
|----------|-------------|
| `NOTION_TOKEN` | Notion integration token |
| `NOTION_TRACKING_DB_ID` | Notion DB for usage tracking |
| `GEMINI_API_KEY` | Service account JSON (full JSON string) for Google Gemini |
| `ADMIN_PASSWORD` | Password for the embedded `admin` user |
| `AUTH_SESSION_ENCRYPTION_KEY` | ≥16 char string for session cookie encryption |

### Optional

| Variable | Default | Description |
|----------|---------|-------------|
| `GITHUB_USERNAME` | `m-idriss` | GitHub username for profile queries |
| `GITHUB_TOKEN` | *(empty)* | GitHub personal access token (rate limit) |
| `NOTION_CMS_DB_ID` | *(empty)* | Notion DB for CMS content |
| `NOTION_QUOTA_DB_ID` | *(empty)* | Notion DB for quota sync |
| `NOTION_USER_ID` | *(empty)* | Notion user ID for page assignments |
| `GEMINI_MODEL` | `gemini-2.0-flash-lite-preview-02-05` | Gemini model name |
| `GEMINI_BASE_MESSAGE` | *(empty)* | User prompt template (use `{today}` and `{tz}` placeholders) |
| `GEMINI_SYSTEM_PROMPT` | *(empty)* | System-level Gemini prompt |
| `PORT` | `8080` | HTTP server port |
| `GOOGLE_CLOUD_PROJECT` | *(empty)* | GCP project ID for telemetry |

---

## API Endpoints

### Public (no auth required)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/converter` | Convert images to `.ics` (body: `ConverterRequest`) |
| `GET` | `/converter/quota-status?userId=` | Get user quota status |
| `GET` | `/converter/statistics` | Global usage statistics |
| `GET` | `/github/user` | GitHub profile info |
| `GET` | `/github/social` | GitHub social accounts |
| `GET` | `/github/commits?months=N` | Monthly commit stats (1-60 months) |
| `GET` | `/notion/cms` | CMS content grouped by category |
| `GET` | `/health` | Combined health check |

### Admin (requires `admin` role via form login)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/users` | List all user quota records |
| `GET` | `/users/{userId}` | Get specific user quota |
| `PATCH` | `/users/{userId}` | Update user quota fields |
| `DELETE` | `/users/{userId}` | Remove user record |
| `GET` | `/users/sync-notion` | Push all users: Firestore → Notion |
| `GET` | `/users/sync-firebase` | Pull all users: Notion → Firestore |
| `GET` | `/users/sync-notion-single?userId=` | Push single user to Notion |
| `GET` | `/users/sync-firebase-single?userId=` | Pull single user from Notion |
| `GET` | `/api-docs` | Swagger UI |
| `GET` | `/api-schema` | OpenAPI JSON |
| `GET` | `/` | Redirect to `/api-docs` |

### Authentication

- Admin login: `POST /j_security_check` (form: `j_username`, `j_password`)
- Login page: `/login.html`, error page: `/login-error.html`
- Session cookie name: `quarkus-credential`
- Credentials: username `admin`, password from `ADMIN_PASSWORD` env var (default: `password`)

---

## Quota System

Plans are defined in `PlanType` and limits enforced in `QuotaService`:

| Plan | Monthly Limit |
|------|--------------|
| `FREE` | 10 conversions |
| `PRO` | 100 conversions |
| `UNLIMITED` | 1,000,000 conversions |

- New users are created automatically on first conversion request with `FREE` plan
- Usage resets at the start of each calendar month (UTC)
- Quota data is stored in Firestore collection `users` (document ID = `userId`)
- After each conversion, quota is asynchronously synced to Notion (non-blocking; failures are warned, not thrown)

---

## Exception Handling

All exceptions flow through `GlobalExceptionMapper` which implements `ExceptionMapper<Throwable>`.

### Exception Hierarchy

```
RuntimeException
└── BusinessException (abstract, mapped to 4xx)
    ├── ValidationException   → 400 Bad Request    (code: VALIDATION_ERROR)
    ├── QuotaException        → 429 Too Many Req.  (code: QUOTA_EXCEEDED)
    ├── ProcessingException   → 422 Unprocessable  (code: PROCESSING_ERROR)
    └── ExternalServiceException → 502 Bad Gateway (code: EXTERNAL_SERVICE_ERROR)
```

### Standard Error Response Shape

```json
{
  "success": false,
  "error": "Validation",
  "message": "Human-readable description",
  "errorCode": "VALIDATION_ERROR",
  "details": { "...": "..." },
  "timestamp": "2026-02-21T00:00:00Z",
  "path": "/converter",
  "status": 400
}
```

When adding new error types, extend `BusinessException` and implement `getHttpStatusCode()`. Do not throw raw `RuntimeException` from services.

---

## Caching

Caching uses `@CacheResult` (Quarkus Cache / Caffeine). Cache names and TTLs:

| Cache Name | TTL | Used In |
|-----------|-----|---------|
| `github-user-cache` | 1 hour | `GitHubService.getUserInfo()` |
| `github-social-cache` | 1 hour | `GitHubService.getSocialAccounts()` |
| `github-commits-cache` | 1 hour | `GitHubService.getCommits()` |
| `notion-cms-cache` | 30 minutes | `NotionService.getCmsContent()` |
| `statistics-cache` | 5 minutes | `TrackingService.getStatistics()` |

---

## OpenAPI Profiles

Endpoints are grouped into API profiles using `@Extension`:

| Profile | Annotation | Schema Path | Audience |
|---------|-----------|-------------|----------|
| `public` | `@Extension(name = "x-smallrye-profile-public", value = "")` | `/api-schema/public` | Frontend developers |
| `admin` | `@Extension(name = "x-smallrye-profile-admin", value = "")` | `/api-schema/admin` | Admin operators |
| all | *(no extension)* | `/api-schema` | Everything combined |

Always annotate new resource classes with the appropriate profile extension.

---

## CORS

| Environment | Allowed Origins |
|------------|----------------|
| Production | `https://www.3dime.com`, `https://3dime.com`, `https://www.photocalia.com`, `https://photocalia.com` |
| Dev (`%dev`) | `*` (all origins) |

Allowed methods: `GET, PUT, POST, DELETE, PATCH, OPTIONS`
Max age: 24 hours

---

## Observability

### Logging
- **Dev**: Console text format with timestamps
- **Prod**: JSON structured logs (`quarkus-logging-json`). Key remapping: `level` → `severity`, `timestamp` → `time`. Additional fields: `service=3dime-api`, `version=1.0.0`

Use `@Slf4j` (Lombok) on every class that logs. Log at `INFO` for endpoint entry, `WARN` for recoverable issues, `ERROR` for exceptions.

### Tracing
- Provider: OpenTelemetry
- Exporter: OTLP gRPC → `https://otel.googleapis.com:4317` (Google Cloud Trace)
- Disabled in dev (`%dev.quarkus.otel.traces.enabled=false`)
- Service name attribute: `service.name=3dime-api`

---

## Google Cloud Gemini Integration

- Authentication: Service account JSON stored in `GEMINI_API_KEY` env var (full JSON string)
- Token cached in-memory with double-checked locking in `GeminiService`
- OAuth2 scope: `https://www.googleapis.com/auth/generative-language`
- REST client: `GeminiClient` → `POST /v1beta/models/{model}:generateContent`
- Default model: `gemini-2.0-flash-lite-preview-02-05`
- ICS validation: response must start with `BEGIN:VCALENDAR`, contain `BEGIN:VEVENT`, end with `END:VCALENDAR`
- URL-based images are not yet supported (logged as warning, skipped)

---

## Deployment

### Google Cloud Run (Buildpacks — no Dockerfile needed)

```bash
gcloud run deploy 3dime-api \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated
```

`project.toml` configures buildpack behavior:
- Java 21 JVM
- Build: `mvn clean package -DskipTests`
- Artifact: `target/3dime-api-runner.jar` (uber-jar)

### Packaging

The app builds as an **uber-jar** (`quarkus.package.jar.type=uber-jar`). The final artifact is `target/3dime-api-runner.jar`.

A native image profile exists (`-Pnative`) but is not yet production-ready (see ROADMAP).

---

## Key Conventions

1. **Feature-based packaging** — all code for a domain goes in `com.dime.api.feature.<name>`. No shared `service/` or `controller/` layers.

2. **Lombok** — use `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Slf4j`, `@NonNull` consistently. Do not write boilerplate getters/setters by hand.

3. **Firestore models** — annotate with `@IgnoreExtraProperties`. Store enum fields as strings (`plan = planType.name()`), provide `getPlanType()` / `setPlanType()` accessors.

4. **Configuration injection** — use `@ConfigProperty(name = "...")` with `Optional<String>` for truly optional values. Never use `System.getenv()` directly.

5. **REST clients** — define as interfaces with `@RegisterRestClient(configKey = "...")`. Register the URL in `application.properties` using `quarkus.rest-client."<configKey>".url=`.

6. **Exception handling** — throw `BusinessException` subclasses for expected errors. Never let Firestore or Notion exceptions propagate to callers unchecked; catch, log, and handle gracefully.

7. **Logging** — always include the `userId` or resource identifier in log messages. Follow the pattern:
   ```java
   log.info("GET /endpoint endpoint called for user: {}", userId);
   ```

8. **OpenAPI documentation** — annotate all resources with `@Tag`, `@Operation`, `@APIResponse`, and the appropriate profile `@Extension`. Schema implementations should reference actual model classes.

9. **Transactions** — use `firestore.runTransaction(...)` for any read-modify-write operations on Firestore documents to prevent race conditions.

10. **Notion sync** — Notion sync operations are **fire-and-forget** (non-blocking). Wrap them in try-catch and log failures at `WARN` level. Never let Notion sync failures block the main business flow.

---

## Common Pitfalls

- **Firestore blocking calls**: All `firestore.collection(...).get().get()` calls block the current thread. This is acceptable in the current architecture (Quarkus blocking thread model), but be aware of it when adding new Firestore interactions.
- **Missing `.env` file**: The app starts without `.env` silently, but required env vars (`NOTION_TOKEN`, `NOTION_TRACKING_DB_ID`) must be present. Missing required config causes startup failures.
- **Gemini token refresh**: `GoogleCredentials.refreshIfExpired()` is called on each request. The credentials object itself is cached (singleton in `GeminiService`).
- **Admin password default**: The embedded admin password defaults to `password` if `ADMIN_PASSWORD` is not set. Always set this in production.
- **CORS in dev**: All origins are allowed in dev mode. Do not rely on CORS as a security boundary.
- **Test port**: Tests use a random port (`%test.quarkus.http.test-port=0`). REST-Assured is auto-configured by `@QuarkusTest` to use the correct port.
