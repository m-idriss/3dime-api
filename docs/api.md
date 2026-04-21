# API Reference

## Public Endpoints

### Converter & Quotas

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/converter` | Convert images to `.ics` format |
| `GET` | `/converter/quota-status?userId=` | Get user quota status |
| `GET` | `/converter/statistics` | Global usage statistics |

### GitHub

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/github/user` | GitHub profile info |
| `GET` | `/github/social` | Social account links |
| `GET` | `/github/commits?months=N` | Monthly commit stats (1-60 months) |

### Notion CMS

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/notion/cms` | Categorized CMS content |

### System

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/health` | Combined health check |
| `GET` | `/health/live` | Liveness probe |
| `GET` | `/health/ready` | Readiness probe (checks all dependencies) |
| `GET` | `/api-docs` | Interactive Swagger UI (admin) |
| `GET` | `/api-schema` | Full OpenAPI JSON |
| `GET` | `/api-schema/public` | Public-facing OpenAPI schema |
| `GET` | `/api-schema/admin` | Admin OpenAPI schema |

---

## Admin Endpoints

Requires `admin` role. Login via `POST /j_security_check` (form fields: `j_username`, `j_password`).

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/users` | List all user quota records |
| `GET` | `/users/{userId}` | Get specific user quota |
| `PATCH` | `/users/{userId}` | Update user quota fields |
| `DELETE` | `/users/{userId}` | Remove user record |
| `GET` | `/users/sync-notion` | Push all users: Firestore -> Notion |
| `GET` | `/users/sync-firebase` | Pull all users: Notion -> Firestore |
| `GET` | `/users/sync-notion-single?userId=` | Push single user to Notion |
| `GET` | `/users/sync-firebase-single?userId=` | Pull single user from Notion |

### Authentication

- Login: `POST /j_security_check` (form: `j_username`, `j_password`)
- Login page: `/login.html`, error page: `/login-error.html`
- Session cookie: `quarkus-credential`
- Credentials: username `admin`, password from `ADMIN_PASSWORD` env var (default: `password`)

---

## Example Usage

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

## Quota Plans

New users are created automatically on first conversion with the `FREE` plan. Usage resets at the start of each calendar month (UTC).

| Plan | Monthly Conversions |
|------|-------------------|
| `FREE` | 3 |
| `PRO` | 100 |
| `UNLIMITED` | 1,000,000 |

Quota data is stored in Firestore (`users` collection) and optionally synced to Notion.

---

## Error Responses

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
