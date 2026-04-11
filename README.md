<div align="center">

# 3dime-api

</div>

<div align="center">

[![Live API](https://img.shields.io/badge/Live_API-api.3dime.com-4285F4?style=for-the-badge)](https://api.3dime.com/health)
[![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.31.3-red?style=for-the-badge&logo=quarkus&logoColor=white)](https://quarkus.io/)
[![Google Cloud](https://img.shields.io/badge/Google_Cloud-Run-4285F4?style=for-the-badge&logo=google-cloud&logoColor=white)](https://cloud.google.com/run)
[![Maven](https://img.shields.io/badge/Maven-3.8%2B-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![Security](https://img.shields.io/badge/Security-Hardened-00D4AA?style=for-the-badge)](docs/security.md)

</div>

**Production-grade REST API** powering AI-powered image-to-calendar conversion at scale. Built with Quarkus on Google Cloud Run, serving [3dime.com](https://3dime.com) and [photocalia.com](https://photocalia.com) with real-time conversions, GitHub integration, Notion CMS, and granular quota management.

**Security-first architecture:** Firebase ID token validation, rate limiting, file validation, CSP headers, and OWASP hardening included.

**Frontend:** [Photocalia](https://github.com/m-idriss/photocalia) (Angular SaaS)

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
