# Configuration

## Priority Order

Configuration is loaded in priority order (highest wins):

| Priority | Source | Ordinal |
|----------|--------|---------|
| System properties | JVM `-D` flags | 400 |
| Environment variables | OS env | 300 |
| `.env` file | `DotEnvConfigSource` | 290 |
| `application.properties` | Quarkus default | 100 |

The `.env` file is loaded via `dotenv-java` through `DotEnvConfigSource`. It is optional (silently ignored if absent) and intended for local development only. Never commit `.env` to version control.

---

## Required Environment Variables

| Variable | Description |
|----------|-------------|
| `NOTION_TOKEN` | Notion integration token |
| `NOTION_TRACKING_DB_ID` | Notion DB ID for usage analytics |
| `GEMINI_API_KEY` | Service account JSON (full JSON string) for Gemini API auth |
| `ADMIN_PASSWORD` | Password for the embedded `admin` user |
| `AUTH_SESSION_ENCRYPTION_KEY` | >=16-character string for session cookie encryption |

## Optional Environment Variables

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

## Example `.env` File

```dotenv
NOTION_TOKEN=secret_xxx
NOTION_TRACKING_DB_ID=db_xxx
GEMINI_API_KEY={"type":"service_account",...}
ADMIN_PASSWORD=changeme
AUTH_SESSION_ENCRYPTION_KEY=at-least-16-chars
```

---

## Quarkus Profiles

Quarkus profile prefixes scope configuration to specific environments:

- `%dev.*` -- only in `mvn quarkus:dev`
- `%prod.*` -- only in production jar
- `%test.*` -- only during `mvn test`

## CORS

| Environment | Allowed Origins |
|------------|----------------|
| Production | `https://www.3dime.com`, `https://3dime.com`, `https://www.photocalia.com`, `https://photocalia.com` |
| Dev (`%dev`) | `*` (all origins) |

Allowed methods: `GET, PUT, POST, DELETE, PATCH, OPTIONS` | Max age: 24 hours
