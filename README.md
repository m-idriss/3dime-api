# 3dime-api

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.31.3-blue.svg)](https://quarkus.io/)
[![Google Cloud](https://img.shields.io/badge/Google%20Cloud-Run-4285F4.svg)](https://cloud.google.com/run)
[![Build](https://img.shields.io/badge/Build-Maven-C71A36.svg)](https://maven.apache.org/)
[![API Status](https://img.shields.io/website?url=https%3A%2F%2Fapi.3dime.com%2Fhealth&label=API&up_message=online&down_message=offline)](https://api.3dime.com/health)

> **Production-grade REST API** powering intelligent image-to-calendar conversion at scale

Transform photos into calendar events with AI. [3dime.com](https://3dime.com) and [photocalia.com](https://photocalia.com) rely on this high-performance API for real-time conversions, GitHub integration, Notion CMS, and granular quota management.

**Built with security first.** Firebase authentication, rate limiting, file validation, CSP headers, and OWASP security hardening come standard.

---

## ✨ Capabilities

| Feature | Description |
|---------|-------------|
| 🖼️ **Image-to-Calendar** | AI-powered conversion of photos to `.ics` calendar files using Google Gemini |
| 📊 **GitHub Integration** | Fetch user profiles, social accounts, and monthly commit statistics |
| 📝 **Notion CMS** | Content management integration for tools and resources |
| 📈 **Analytics & Tracking** | Usage statistics and conversion tracking persisted to Notion |
| ⚡ **Quota Management** | Per-user rate limiting with FREE, PRO, and UNLIMITED plans |
| 🔐 **Firebase Authentication** | Server-side ID token validation with automatic key rotation |
| 🛡️ **Rate Limiting** | HTTP rate limiting on all public endpoints via SmallRye Fault Tolerance |
| 📁 **File Validation** | Magic bytes verification for JPEG, PNG, PDF, HEIC formats |
| 🔒 **Session Hardening** | SameSite=Strict cookies with encrypted session keys |
| 📋 **Security Headers** | CSP, OWASP, HSTS, COOP, CORP, X-Frame-Options configured |

---

## 🚀 Get Started

**Requirements:** Java 21+, Maven 3.8+, or Docker

**Set environment variables:**
```dotenv
NOTION_TOKEN=secret_xxx
NOTION_TRACKING_DB_ID=db_xxx
GEMINI_API_KEY={"type":"service_account",...}
ADMIN_PASSWORD=changeme
AUTH_SESSION_ENCRYPTION_KEY=at-least-16-chars
```

**Run locally:**
```bash
mvn quarkus:dev
# → API at http://localhost:8080
# → Docs at http://localhost:8080/api-docs
# → Health at http://localhost:8080/health
```

**Build & run Docker:**
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

---

## 📚 Documentation

[API Reference](docs/api.md), [Configuration](docs/configuration.md), [Architecture](docs/architecture.md), [Deployment](docs/deployment.md), [Health Monitoring](docs/health.md), [Security](docs/security.md)

---

<div align="center">

**Private & Proprietary** • Built by **[Idriss](https://github.com/m-idriss)**

</div>
