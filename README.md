# 3dime-api

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.31.3-blue.svg)](https://quarkus.io/)
[![Google Cloud](https://img.shields.io/badge/Google%20Cloud-Run-4285F4.svg)](https://cloud.google.com/run)
[![Build](https://img.shields.io/badge/Build-Maven-C71A36.svg)](https://maven.apache.org/)

A production-ready REST API built with Quarkus and Java 21, deployed on Google Cloud Run. It powers [3dime.com](https://3dime.com) and [photocalia.com](https://photocalia.com) with AI-powered image-to-calendar conversion, GitHub integration, Notion CMS, and per-user quota management.

---

## 🚀 Features

### Core Functionality
- **🖼️ Image-to-Calendar Converter** — AI-powered conversion of images to `.ics` calendar files using Google Gemini
- **📊 GitHub Integration** — Fetch user profiles, social accounts, and monthly commit statistics
- **📝 Notion CMS** — Content management integration for tools and resources
- **📈 Analytics & Tracking** — Usage statistics and conversion tracking persisted to Notion
- **⚡ Quota Management** — Per-user rate limiting with `FREE`, `PRO`, and `UNLIMITED` plans backed by Firestore

### Technical Features
- **RESTEasy Reactive** — Non-blocking REST APIs and REST clients
- **Health Checks** — Liveness and readiness probes at `/health/live` and `/health/ready`
- **OpenAPI / Swagger UI** — Auto-generated docs at `/api-docs`
- **Fault Tolerance** — `@Retry` and `@Timeout` via SmallRye Fault Tolerance
- **Caching** — In-memory result caching with Quarkus Cache (Caffeine)
- **Structured JSON Logging** — Production-ready log format for Google Cloud Logging
- **OpenTelemetry Tracing** — Distributed traces exported to Google Cloud Trace
- **Admin UI** — Form-based authentication for user quota management

---

## 📋 Prerequisites

- Java 21+
- Maven 3.8+
- Google Cloud account (for Firestore and Cloud Run)
- Notion API token
- Google Cloud service account with Gemini API access

---

## 🏗️ Architecture

The application follows a **feature-based architecture** — code is organized by domain feature, not by technical layer. Each feature owns its full vertical slice (resource → service → client → model).

```
com.dime.api.feature/
├── converter/    # Image-to-calendar conversion, quota management, usage tracking
├── github/       # GitHub profile, social accounts, commit statistics
├── notion/       # Notion CMS content
└── shared/       # Cross-cutting: config, exception handling, health checks
```

---

## ⚙️ Configuration

Configuration is loaded in priority order: system properties → environment variables → `.env` file → `application.properties`.

Create a `.env` file in the project root for local development (never commit it):

```dotenv
NOTION_TOKEN=secret_xxx
NOTION_TRACKING_DB_ID=db_xxx
GEMINI_API_KEY={"type":"service_account",...}
ADMIN_PASSWORD=changeme
AUTH_SESSION_ENCRYPTION_KEY=at-least-16-chars
```

### Required Environment Variables

| Variable | Description |
|----------|-------------|
| `NOTION_TOKEN` | Notion integration token |
| `NOTION_TRACKING_DB_ID` | Notion DB ID for usage analytics |
| `GEMINI_API_KEY` | Service account JSON (full JSON string) for Gemini API auth |
| `ADMIN_PASSWORD` | Password for the embedded `admin` user |
| `AUTH_SESSION_ENCRYPTION_KEY` | ≥16-character string for session cookie encryption |

### Optional Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `GITHUB_USERNAME` | `m-idriss` | GitHub username for profile queries |
| `GITHUB_TOKEN` | *(empty)* | GitHub personal access token (raises rate limits) |
| `NOTION_CMS_DB_ID` | *(empty)* | Notion DB ID for CMS content |
| `NOTION_QUOTA_DB_ID` | *(empty)* | Notion DB ID for quota sync |
| `NOTION_USER_ID` | *(empty)* | Notion user ID for page assignments |
| `GEMINI_MODEL` | `gemini-2.0-flash-lite-preview-02-05` | Gemini model name |
| `GEMINI_BASE_MESSAGE` | *(empty)* | User prompt template (`{today}` and `{tz}` placeholders) |
| `GEMINI_SYSTEM_PROMPT` | *(empty)* | System-level Gemini prompt |
| `PORT` | `8080` | HTTP server port |
| `GOOGLE_CLOUD_PROJECT` | *(empty)* | GCP project ID for telemetry |

---

## 🛠️ Running Locally

### Development Mode (hot reload)

```bash
mvn quarkus:dev
```

| URL | Description |
|-----|-------------|
| `http://localhost:8080` | API root (redirects to Swagger) |
| `http://localhost:8080/api-docs` | Interactive Swagger UI |
| `http://localhost:8080/health` | Combined health check |

### Production Mode

```bash
mvn clean package
java -jar target/3dime-api-runner.jar
```

---

## 🧪 Testing

```bash
# Unit tests only
mvn test

# Unit + integration tests with coverage
mvn verify
```

Tests use `@QuarkusTest` with REST-Assured on a random port. External dependencies (Firestore, Notion) are mocked with Mockito.

---

## 📡 API Reference

### 🖼️ Converter & Quotas

| Method | Endpoint | Auth | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/converter` | Public | Convert images to `.ics` format |
| `GET` | `/converter/quota-status?userId=` | Public | Get user quota status |
| `GET` | `/converter/statistics` | Public | Global usage statistics |

### 📊 GitHub

| Method | Endpoint | Auth | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/github/user` | Public | GitHub profile info |
| `GET` | `/github/social` | Public | Social account links |
| `GET` | `/github/commits?months=N` | Public | Monthly commit stats (1–60 months) |

### 📝 Notion CMS

| Method | Endpoint | Auth | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/notion/cms` | Public | Categorized CMS content |

### 🔐 Admin — User Management

Requires `admin` role. Login via `POST /j_security_check` (form fields: `j_username`, `j_password`).

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/users` | List all user quota records |
| `GET` | `/users/{userId}` | Get specific user quota |
| `PATCH` | `/users/{userId}` | Update user quota fields |
| `DELETE` | `/users/{userId}` | Remove user record |
| `GET` | `/users/sync-notion` | Push all users: Firestore → Notion |
| `GET` | `/users/sync-firebase` | Pull all users: Notion → Firestore |
| `GET` | `/users/sync-notion-single?userId=` | Push single user to Notion |
| `GET` | `/users/sync-firebase-single?userId=` | Pull single user from Notion |

### 🩺 System

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/health` | Combined health check |
| `GET` | `/health/live` | Liveness probe |
| `GET` | `/health/ready` | Readiness probe |
| `GET` | `/api-docs` | Interactive Swagger UI (admin) |
| `GET` | `/api-schema` | Full OpenAPI JSON |
| `GET` | `/api-schema/public` | Public-facing OpenAPI schema |
| `GET` | `/api-schema/admin` | Admin OpenAPI schema |
| `GET` | `/` | Redirect to `/api-docs` |

---

## 📝 Example Usage

### Convert Image to Calendar

```bash
curl -X POST http://localhost:8080/converter \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "files": [
      {
        "dataUrl": "data:image/png;base64,<base64EncodedImage>"
      }
    ]
  }'
```

### Check Quota Status

```bash
curl "http://localhost:8080/converter/quota-status?userId=user123"
```

### Get GitHub Profile

```bash
curl http://localhost:8080/github/user
```

### Get Commit Statistics (last 6 months)

```bash
curl "http://localhost:8080/github/commits?months=6"
```

### Get CMS Content

```bash
curl http://localhost:8080/notion/cms
```

### Get Usage Statistics

```bash
curl http://localhost:8080/converter/statistics
```

---

## 📊 Quota Plans

New users are created automatically on first conversion with the `FREE` plan. Usage resets at the start of each calendar month (UTC).

| Plan | Monthly Conversions |
|------|-------------------|
| `FREE` | 10 |
| `PRO` | 100 |
| `UNLIMITED` | 1,000,000 |

Quota data is stored in Firestore (`users` collection) and optionally synced to Notion.

---

## ⚠️ Error Responses

All errors follow a standard envelope:

```json
{
  "success": false,
  "error": "Validation",
  "message": "Human-readable description",
  "errorCode": "VALIDATION_ERROR",
  "details": {},
  "timestamp": "2026-02-21T00:00:00Z",
  "path": "/converter",
  "status": 400
}
```

| HTTP Status | Error Code | Cause |
|------------|------------|-------|
| `400` | `VALIDATION_ERROR` | Invalid request input |
| `422` | `PROCESSING_ERROR` | Valid input but processing failed |
| `429` | `QUOTA_EXCEEDED` | Monthly conversion limit reached |
| `502` | `EXTERNAL_SERVICE_ERROR` | Upstream API failure (Gemini, Notion, GitHub) |

---

## 🚢 Deployment to Google Cloud Run

No Dockerfile needed — the app uses [Google Cloud Buildpacks](https://buildpacks.io/).

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

The buildpack automatically detects Java 21 from `pom.xml`, builds with Maven, and creates an optimized container.

---

## 🛠️ Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | [Quarkus 3.31.3](https://quarkus.io/) |
| Build | Maven 3.8+ |
| Database | Google Cloud Firestore |
| AI | Google Gemini API |
| External APIs | GitHub REST API, Notion API |
| Observability | OpenTelemetry → Google Cloud Trace; JSON structured logging |
| Resilience | SmallRye Fault Tolerance (`@Retry`, `@Timeout`) |
| Caching | Quarkus Cache (Caffeine) |
| Auth | Quarkus Elytron (form-based) |
| Deployment | Google Cloud Run + Buildpacks |
| Code Generation | Lombok |

---

## 📦 Project Structure

```
3dime-api/
├── src/
│   ├── main/
│   │   ├── java/com/dime/api/
│   │   │   ├── DimeApplication.java
│   │   │   └── feature/
│   │   │       ├── converter/    # Image-to-calendar, quota, tracking
│   │   │       ├── github/       # GitHub profile and stats
│   │   │       ├── notion/       # Notion CMS
│   │   │       └── shared/       # Config, exceptions, health
│   │   └── resources/
│   │       └── application.properties
│   └── test/                     # JUnit 5 + REST-Assured tests
├── pom.xml                       # Maven build
├── project.toml                  # Buildpack config
└── README.md
```

---

## 📄 License

This project is private and proprietary.

## 👤 Author

**Idriss** — [@m-idriss](https://github.com/m-idriss)

## 🔗 Links

- [Quarkus Documentation](https://quarkus.io/guides/)
- [Google Cloud Run Documentation](https://cloud.google.com/run/docs)
- [Buildpacks Documentation](https://buildpacks.io/)
- API Documentation: `/api-docs` (when running)
