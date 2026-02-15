# 3dime-api

A production-ready Quarkus Java 17 REST API for Google Cloud Run.

## Features

- **GET /github/user** - Fetches GitHub user information from the configured username
- **GET /health** - Health check endpoint
- Configured for Google Cloud Run deployment with Buildpacks (no Dockerfile needed)
- Layered architecture (Resource → Service → Client)
- REST Client Reactive for external API calls
- Comprehensive logging
- Error handling with 502 Bad Gateway on external API failures

## Configuration

The application reads configuration from `application.properties`:

```properties
github.username=m-idriss
```

This can be overridden using the `GITHUB_USERNAME` environment variable.

The server listens on `0.0.0.0` and port `8080` by default. The port can be overridden using the `PORT` environment variable.

## Running Locally

### Development Mode

```bash
mvn quarkus:dev
```

### Production Mode

```bash
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

## Testing

```bash
mvn test
```

## Deployment to Google Cloud Run

This application is designed to work with Cloud Run Buildpacks (no Dockerfile required):

```bash
gcloud run deploy 3dime-api \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars "GITHUB_USERNAME=m-idriss"
```

The buildpack configuration is in `project.toml`.

## API Endpoints

### Get GitHub User
```bash
curl http://localhost:8080/github/user
```

Returns the GitHub user profile for the configured username.

### Health Check
```bash
curl http://localhost:8080/health
```

Returns the application health status.

## Architecture

- **Resource Layer** (`com.threedime.api.resource`): REST endpoints
- **Service Layer** (`com.threedime.api.service`): Business logic
- **Client Layer** (`com.threedime.api.client`): External API integration
