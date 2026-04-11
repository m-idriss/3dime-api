# 3dime-api

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.31.3-blue.svg)](https://quarkus.io/)
[![Google Cloud](https://img.shields.io/badge/Google%20Cloud-Run-4285F4.svg)](https://cloud.google.com/run)
[![Build](https://img.shields.io/badge/Build-Maven-C71A36.svg)](https://maven.apache.org/)
[![API Status](https://img.shields.io/website?url=https%3A%2F%2Fapi.3dime.com%2Fhealth&label=API&up_message=online&down_message=offline)](https://api.3dime.com/health)

REST API powering [3dime.com](https://3dime.com) and [photocalia.com](https://photocalia.com) — AI image-to-calendar conversion, GitHub integration, Notion CMS, and per-user quota management.

Hardened for production with **Firebase ID token validation**, **rate limiting**, **magic bytes file validation**, **content security policies**, and **structured logging**.

---

## 🚀 Features

### Core Functionality
- **🖼️ Image-to-Calendar Converter** — AI-powered conversion of images to `.ics` calendar files using Google Gemini
- **📊 GitHub Integration** — Fetch user profiles, social accounts, and monthly commit statistics
- **📝 Notion CMS** — Content management integration for tools and resources
- **📈 Analytics & Tracking** — Usage statistics and conversion tracking persisted to Notion
- **⚡ Quota Management** — Per-user rate limiting with `FREE`, `PRO`, and `UNLIMITED` plans backed by Firestore

### Security Features
- **🔐 Firebase ID Token Validation** — Server-side verification of Firebase authentication tokens with automatic key rotation
- **🛡️ Rate Limiting** — HTTP rate limiting via SmallRye Fault Tolerance (@RateLimit)
- **📁 File Upload Validation** — Magic bytes verification for JPEG, PNG, PDF, HEIC formats
- **🔒 Session Hardening** — SameSite=Strict cookies, encrypted session keys
- **📋 Content Security Policy** — CSP headers, CORS restrictions, X-Frame-Options, X-Content-Type-Options
- **📝 OWASP Security Headers** — All top 10 mitigations including HSTS, CORP, COOP

### Technical Features
- **RESTEasy Reactive** — Non-blocking REST APIs and REST clients
- **Health Checks** — Liveness probe at `/health/live`; readiness probe at `/health/ready` with per-dependency checks (Firestore, Gemini, Notion, GitHub), criticality levels, latency reporting, and 15 s result caching
- **OpenAPI / Swagger UI** — Auto-generated docs at `/api-docs` with profile-based schemas (public/admin)
- **Fault Tolerance** — `@Retry` and `@Timeout` via SmallRye Fault Tolerance
- **Caching** — In-memory result caching with Quarkus Cache (Caffeine)
- **Structured JSON Logging** — Production-ready log format for Google Cloud Logging
- **OpenTelemetry Tracing** — Distributed traces exported to Google Cloud Trace
- **Admin UI** — Form-based authentication with SHA-512 password hashing

---
## Quick Start

**Prerequisites:** Java 21+ and Maven 3.8+ (for dev mode), or Docker (for containerized build)

### Local Development

1. Create a `.env` file at the project root (see [Configuration](docs/configuration.md)):

```dotenv
NOTION_TOKEN=secret_xxx
NOTION_TRACKING_DB_ID=db_xxx
GEMINI_API_KEY={"type":"service_account",...}
ADMIN_PASSWORD=changeme
AUTH_SESSION_ENCRYPTION_KEY=at-least-16-chars
```

2. Run in dev mode with hot reload:

```bash
mvn quarkus:dev
```

3. Open in browser:

| URL | Description |
|-----|-------------|
| http://localhost:8080 | API root |
| http://localhost:8080/api-docs | Swagger UI |
| http://localhost:8080/health | Health check |

### Docker Build (Local Testing)

```bash
docker build -t 3dime-api:latest .
docker run -p 8080:8080 \
  -e NOTION_TOKEN=secret_xxx \
  -e NOTION_TRACKING_DB_ID=db_xxx \
  -e GEMINI_API_KEY='{"type":"service_account",...}' \
  -e ADMIN_PASSWORD=changeme \
  -e AUTH_SESSION_ENCRYPTION_KEY=at-least-16-chars \
  3dime-api:latest
```

The `Dockerfile` uses a two-stage build: Maven compiles the application in the builder stage, then the runtime stage uses a minimal Alpine JRE image (~27 MB base).

## Commands

```bash
# Development & Testing
mvn quarkus:dev          # Dev mode with hot reload
mvn test                 # Unit tests
mvn verify               # Unit + integration tests
mvn clean package        # Build uber-jar to target/

# Docker Build (Local)
docker build -t 3dime-api:latest .
docker run -p 8080:8080 3dime-api:latest

# Cloud Deployment
# Configured via Cloud Build trigger in GCP Console (uses Dockerfile + Docker builder)
```

## Documentation

| Document | Description |
|----------|-------------|
| [API Reference](docs/api.md) | Endpoints, examples, error responses, quota plans |
| [Configuration](docs/configuration.md) | Environment variables, `.env` setup, CORS |
| [Architecture](docs/architecture.md) | Project structure, tech stack, caching |
| [Deployment](docs/deployment.md) | Docker, Cloud Build, Cloud Run, observability |
| [Health Monitoring](docs/health.md) | Dependency checks, status logic |
| [Security](docs/security.md) | Firebase ID tokens, rate limiting, file validation, OWASP headers |

## License

This project is private and proprietary.

## Author

**Idriss** — [@m-idriss](https://github.com/m-idriss)
