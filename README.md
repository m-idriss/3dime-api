# 3dime-api

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.6.4-blue.svg)](https://quarkus.io/)
[![Google Cloud](https://img.shields.io/badge/Google%20Cloud-Run-4285F4.svg)](https://cloud.google.com/run)

A production-ready REST API built with Quarkus and Java 21, designed for Google Cloud Run deployment. This API provides AI-powered image-to-calendar conversion, GitHub integration, Notion CMS content management, and usage analytics.

## 🚀 Features

### Core Functionality
- **🖼️ Image-to-Calendar Converter** - AI-powered conversion of images to .ics calendar files using Google Gemini
- **📊 GitHub Integration** - Fetch user profiles, social accounts, and commit statistics
- **📝 Notion CMS** - Content management system integration for tools and resources
- **📈 Analytics & Tracking** - Usage statistics and conversion tracking with Firestore
- **⚡ Quota Management** - User-based rate limiting with configurable plans

### Technical Features
- **Reactive REST APIs** - Built with RESTEasy Reactive and REST Client Reactive
- **Health Checks** - Liveness and readiness probes for production monitoring
- **OpenAPI/Swagger** - Auto-generated API documentation at `/api-docs`
- **Fault Tolerance** - Retry logic and timeouts with SmallRye Fault Tolerance
- **JSON Logging** - Structured logging for production environments
- **Firestore Integration** - Cloud-native data persistence with Google Cloud Firestore

## 📋 Prerequisites

- Java 21 or higher
- Maven 3.8+
- Google Cloud account (for deployment)
- Notion API token
- Google Cloud service account with Gemini API access (used via Application Default Credentials, e.g., on Cloud Run)

## 🏗️ Architecture

The application follows a **feature-based architecture** where code is organized by domain/feature rather than by technical layer. This promotes better modularity, maintainability, and domain-driven design:

- **Converter Feature** (`com.dime.api.feature.converter`) - Image-to-calendar conversion
  - Resources, services, clients, and models for AI-powered image conversion
  - Includes quota management and tracking services
  
- **GitHub Feature** (`com.dime.api.feature.github`) - GitHub integration
  - Resources, services, clients, and models for GitHub API operations
  - User profiles, social accounts, and commit statistics
  
- **Notion Feature** (`com.dime.api.feature.notion`) - Notion CMS
  - Resources, services, and clients for Notion database integration
  - CMS content management
  
- **Statistics Feature** (`com.dime.api.feature.statistics`) - Analytics
  - Resources for usage statistics and analytics
  
- **Shared Components** (`com.dime.api.feature.shared`) - Cross-cutting concerns
  - Configuration, health checks, and common utilities

## ⚙️ Configuration

The application uses environment variables for configuration. Create a `.env` file in the root directory or set these as environment variables:

### Required Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `NOTION_TOKEN` | Notion API integration token | *(none)* |
| `NOTION_TRACKING_DB_ID` | Notion database ID for tracking | *(none)* |

### Optional Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `GITHUB_USERNAME` | GitHub username for profile queries | `m-idriss` |
| `NOTION_CMS_DB_ID` | Notion database ID for CMS content | *(empty)* |
| `NOTION_QUOTA_DB_ID` | Notion database ID for quota management | *(empty)* |
| `GEMINI_MODEL` | Google Gemini model identifier | *(empty)* |
| `GEMINI_BASE_MESSAGE` | Base message for Gemini prompts | *(empty)* |
| `GEMINI_SYSTEM_PROMPT` | System prompt for Gemini | *(empty)* |
| `PORT` | HTTP server port | `8080` |

### Configuration Files

- `src/main/resources/application.properties` - Application configuration
- `project.toml` - Buildpack configuration for Google Cloud deployment

## 🛠️ Running Locally

### Development Mode (with hot reload)

```bash
mvn quarkus:dev
```

Access the application at:
- API: `http://localhost:8080`
- API Documentation: `http://localhost:8080/api-docs`
- Health Check: `http://localhost:8080/health`

### Production Mode

```bash
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

## 🧪 Testing

Run all tests:

```bash
mvn test
```

Run with coverage:

```bash
mvn verify
```

## 📡 API Endpoints

### Image-to-Calendar Converter

**POST** `/converter`
- Converts images containing calendar events to .ics format
- Request body (JSON):
  ```json
  {
    "userId": "string",
    "timeZone": "string (optional, e.g. \"America/New_York\")",
    "currentDate": "string (optional, ISO-8601 date, e.g. \"2024-01-31\")",
    "files": [
      {
        "dataUrl": "string (optional, data URL with base64 image data)",
        "url": "string (optional, publicly accessible image URL)"
      }
    ]
  }
- Returns: JSON response with `icsContent` field containing the ICS calendar file
- Includes quota checking and usage tracking

### GitHub Endpoints

**GET** `/github/user`
- Returns GitHub user profile information
- Response: User object with profile details

**GET** `/github/social`
- Returns GitHub user's social accounts
- Response: Array of social account links

**GET** `/github/commits?months=12`
- Returns commit statistics over specified months
- Query param: `months` (default: 12)
- Response: Array of commit data per month

### Notion CMS

**GET** `/notion/cms`
- Fetches grouped content from Notion CMS database
- Response: Map of content items grouped by category

### Analytics

**GET** `/statistics`
- Returns usage statistics and analytics
- Response: Statistics object with conversion metrics

### Health & Documentation

**GET** `/health`
- Health check endpoint (liveness & readiness probes)
- Response: Health status

**GET** `/api-docs`
- Interactive Swagger UI for API documentation

**GET** `/api-schema`
- OpenAPI schema definition

**GET** `/`
- Redirects to `/api-docs`

## 📝 Example Usage

### Convert Image to Calendar

```bash
curl -X POST http://localhost:8080/converter \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "files": [
      {
        "dataUrl": "data:image/png;base64,base64EncodedImage..."
      }
    ]
  }'
```

### Get GitHub User Info

```bash
curl http://localhost:8080/github/user
```

### Get Commit Statistics

```bash
curl http://localhost:8080/github/commits?months=6
```

### Get CMS Content

```bash
curl http://localhost:8080/notion/cms
```

### Check Statistics

```bash
curl http://localhost:8080/statistics
```

## 🚢 Deployment to Google Cloud Run

This application is optimized for Google Cloud Run with buildpack deployment (no Dockerfile required):

### Deploy with Default Settings

```bash
gcloud run deploy 3dime-api \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated
```

### Deploy with Environment Variables

```bash
gcloud run deploy 3dime-api \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars "GITHUB_USERNAME=m-idriss,NOTION_TOKEN=secret_xxx,NOTION_TRACKING_DB_ID=db_xxx"
```

### Deploy with Secrets (Recommended for Production)

```bash
gcloud run deploy 3dime-api \
  --source . \
  --platform managed \
  --region us-central1 \
  --set-secrets "NOTION_TOKEN=notion-token:latest,GEMINI_API_KEY=gemini-key:latest"
```

The buildpack automatically:
- Detects Java 21 from `pom.xml`
- Builds the application with Maven
- Creates an optimized container image
- Configures the runtime environment

## 🛠️ Technology Stack

- **Framework**: [Quarkus 3.6.4](https://quarkus.io/) - Supersonic Subatomic Java
- **Language**: Java 21
- **Build Tool**: Maven
- **Database**: Google Cloud Firestore
- **AI Integration**: Google Gemini API
- **External APIs**: GitHub API, Notion API
- **Deployment**: Google Cloud Run with Buildpacks
- **Monitoring**: SmallRye Health
- **Documentation**: SmallRye OpenAPI / Swagger UI
- **Resilience**: SmallRye Fault Tolerance
- **Logging**: JSON structured logging

## 📦 Project Structure

```
3dime-api/
├── src/
│   ├── main/
│   │   ├── java/com/dime/api/
│   │   │   ├── feature/
│   │   │   │   ├── converter/    # Image-to-calendar conversion feature
│   │   │   │   ├── github/       # GitHub integration feature
│   │   │   │   ├── notion/       # Notion CMS feature
│   │   │   │   ├── statistics/   # Analytics feature
│   │   │   │   └── shared/       # Shared components (config, health)
│   │   │   └── DimeApplication.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/                     # Unit and integration tests
├── pom.xml                       # Maven dependencies
├── project.toml                  # Buildpack configuration
└── README.md
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is private and proprietary.

## 👤 Author

**Idriss** - [@m-idriss](https://github.com/m-idriss)

## 🔗 Links

- [Quarkus Documentation](https://quarkus.io/guides/)
- [Google Cloud Run Documentation](https://cloud.google.com/run/docs)
- [Buildpacks Documentation](https://buildpacks.io/)
- API Documentation: `/api-docs` (available when running the application)
