# Architecture

## Feature-Based Packages

Code is organized by domain feature, not by technical layer. Each feature owns its full vertical slice:

```
feature/<name>/
  <Name>Resource.java    -- JAX-RS endpoint (HTTP layer)
  <Name>Service.java     -- Business logic
  <Name>Client.java      -- MicroProfile REST client (external API)
  <Model>.java           -- Request/response/domain models
```

## Project Structure

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
├── docs/                         # Detailed documentation
├── pom.xml                       # Maven build
├── project.toml                  # Buildpack config
└── README.md
```

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Quarkus 3.39.2 |
| Build | Maven 3.8+ |
| Database | Google Cloud Firestore |
| AI | Google Gemini API or Anthropic Claude behind a shared provider boundary |
| External APIs | GitHub REST API, Notion API |
| Observability | OpenTelemetry -> Google Cloud Trace; JSON structured logging |
| Resilience | SmallRye Fault Tolerance (`@Retry`, `@Timeout`) |
| Caching | Quarkus Cache (Caffeine) |
| Auth | Quarkus Elytron (form-based) |
| Deployment | Google Cloud Run + Buildpacks |
| Code Generation | Lombok |

Provider responses are untrusted input. The converter parses them into a typed event model,
validates titles, ranges, timezones and recurrence, then regenerates RFC 5545 content on the
server. Provider-produced calendar text is never returned directly to clients.

## Caching

| Cache Name | TTL | Used In |
|-----------|-----|---------|
| `github-user-cache` | 1 hour | `GitHubService.getUserInfo()` |
| `github-social-cache` | 1 hour | `GitHubService.getSocialAccounts()` |
| `github-commits-cache` | 1 hour | `GitHubService.getCommits()` |
| `notion-cms-cache` | 30 minutes | `NotionService.getCmsContent()` |
| `statistics-cache` | 5 minutes | `TrackingService.getStatistics()` |
