# Security

Production hardening for 3dime-api includes Firebase authentication, rate limiting, file validation, and comprehensive security headers.

---

## Firebase ID Token Validation

All protected endpoints validate Firebase ID tokens on the server side, preventing client-side token spoofing and user impersonation.

### How It Works

1. **Frontend** (Angular): Obtains Firebase ID token from Firebase Authentication
2. **Frontend**: Includes token in `Authorization: Bearer <token>` header
3. **Backend** (Quarkus): `FirebaseAuthFilter` intercepts requests
4. **Filter**: Calls `FirebaseAuth.getInstance().verifyIdToken(token)`
5. **Filter**: If valid, stores verified `uid` and `email` as request attributes
6. **Endpoint**: Uses verified identity instead of trusting client-provided `userId`

**Key benefit**: Eliminates user impersonation vulnerability. Unauthenticated requests are treated as anonymous (fallback for public endpoints).

### Implementation

- **FirebaseConfig.java**: Initializes Firebase Admin SDK from service account credentials (`GEMINI_API_KEY`)
- **FirebaseAuthFilter.java**: JAX-RS `ContainerRequestFilter` that validates tokens on every request
- **Automatic key rotation**: Firebase Admin SDK handles key rotation transparently

### Endpoints Protected

| Endpoint | Auth Required | Behavior |
|----------|---------------|----------|
| `POST /converter` | Optional | Anonymous: tracks as `system`; Authenticated: uses verified `uid` |
| `GET /converter/quota-status?userId=` | Optional | Uses authenticated `uid` if available, otherwise query param |
| `GET /converter/statistics` | No | Public |
| Admin endpoints (`/users/*`) | Yes | Requires form-based login (not Firebase) |

---

## Rate Limiting

HTTP rate limiting via SmallRye Fault Tolerance (`@RateLimit`) prevents brute force attacks and resource exhaustion.

### Implementation

Two mechanisms provide rate limiting:

1. **@RateLimit Annotation** (SmallRye Fault Tolerance)
   - Applied to: `POST /converter`, `GET /github/*`, `GET /notion/*`
   - Syntax: `@RateLimit(value = 10, window = 1, windowUnit = ChronoUnit.MINUTES)`
   - Tracks per-thread; effective for high-load endpoints

2. **LoginRateLimitFilter** (JAX-RS ContainerRequestFilter)
   - Applied to: `POST /j_security_check` (admin login)
   - Tracks per-IP using `X-Forwarded-For` header (Cloud Run compatibility)
   - Stores attempt timestamps in in-memory ConcurrentHashMap
   - Cleans up old attempts automatically when new request arrives

### Rate Limits by Endpoint

| Endpoint | Limit | Mechanism |
|----------|-------|-----------|
| `POST /converter` | 10 requests/minute | @RateLimit |
| `GET /github/*` | 10 requests/minute | @RateLimit |
| `GET /notion/*` | 10 requests/minute | @RateLimit |
| `POST /j_security_check` (admin login) | 5 requests/5 minutes | LoginRateLimitFilter (per-IP) |
| `GET /converter/quota-status` | 30 requests/minute | @RateLimit |

**Note on IP detection**: LoginRateLimitFilter checks `X-Forwarded-For` header first (Cloud Run proxy), then `X-Real-IP`, then falls back to connection IP. This ensures accurate per-IP rate limiting even behind reverse proxies.

---

## File Upload Validation

Image uploads are validated using magic bytes (file signatures) before processing to prevent malicious file uploads.

### Supported Formats

| Format | Magic Bytes | Use Case |
|--------|------------|----------|
| JPEG | `FF D8 FF` | Photo upload |
| PNG | `89 50 4E 47` | Diagram, screenshot |
| PDF | `25 50 44 46` | Document upload |
| HEIC | `00 00 00 18 66 74 79 70` | iPhone photos |

### Implementation

- **Validation point**: `ConverterResource.validateFileFormat(byte[] data, String fileName)`
- **Behavior**: Rejects files with incorrect magic bytes → HTTP 400 Bad Request
- **MIME type**: Also verified against declared Content-Type header
- **Size limit**: 10 MB max file size (configured in `application.properties`)

### Error Response

```json
{
  "success": false,
  "error": "Validation",
  "message": "Unsupported file format. Allowed: JPEG, PNG, PDF, HEIC",
  "errorCode": "VALIDATION_ERROR",
  "status": 400
}
```

---

## Session Hardening

Web sessions (admin login) are hardened using secure cookies and encryption.

### Cookie Configuration

| Setting | Value | Purpose |
|---------|-------|---------|
| **SameSite** | `Strict` | Prevents CSRF attacks (no cross-site requests) |
| **HttpOnly** | Yes | Prevents JavaScript access (XSS mitigation) |
| **Secure** | Yes (prod) | HTTPS only |
| **Encrypted** | Yes | Session keys encrypted with `AUTH_SESSION_ENCRYPTION_KEY` |

**Quarkus config**: `quarkus.http.auth.form.cookie-same-site=strict`

### Session Encryption

- **Key source**: `AUTH_SESSION_ENCRYPTION_KEY` environment variable (≥16 characters)
- **Algorithm**: AES-256
- **Length requirement**: Exactly 32 bytes for AES-256 (provided key is hashed)

---

## Content Security Policy (CSP)

CSP header restricts which resources the browser can load, mitigating XSS and injection attacks.

### Header Value

```
default-src 'self'; 
script-src 'self'; 
style-src 'self'; 
img-src 'self' data:; 
font-src 'self'
```

### Directives

| Directive | Behavior |
|-----------|----------|
| **default-src 'self'** | Only load resources from same origin by default |
| **script-src 'self'** | Only execute scripts from same origin (no inline scripts) |
| **style-src 'self'** | Only load stylesheets from same origin |
| **img-src 'self' data:** | Images from same origin or data URIs |
| **font-src 'self'** | Only load fonts from same origin |

### Configuration

**Quarkus property**:
```properties
quarkus.http.header."Content-Security-Policy".value=default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'
```

---

## OWASP Security Headers

Additional headers to mitigate common attacks:

| Header | Value | Protection |
|--------|-------|-----------|
| **X-Frame-Options** | `DENY` | Prevents clickjacking (no framing) |
| **X-Content-Type-Options** | `nosniff` | Prevents MIME type sniffing |
| **Referrer-Policy** | `strict-origin-when-cross-origin` | Controls referrer leakage |
| **Permissions-Policy** | `geolocation=(), microphone=(), camera=()` | Restricts powerful APIs |
| **X-Permitted-Cross-Domain-Policies** | `none` | Disables Flash/PDF cross-domain |
| **Cross-Origin-Opener-Policy** | `same-origin` | Isolates browsing context |
| **Cross-Origin-Resource-Policy** | `same-origin` | CORP: Only same-origin requests |
| **Strict-Transport-Security** | `max-age=31536000; includeSubDomains` | HSTS: Force HTTPS (1 year) |

### Configuration

**Quarkus property** (example for one header):
```properties
quarkus.http.header."X-Frame-Options".value=DENY
quarkus.http.header."X-Content-Type-Options".value=nosniff
quarkus.http.header."Strict-Transport-Security".value=max-age=31536000; includeSubDomains
```

---

## CORS Restrictions

Cross-Origin Resource Sharing is restricted to known frontend origins only.

### Allowed Origins (Production)

```
https://3dime.com
https://www.3dime.com
https://photocalia.com
https://www.photocalia.com
```

### Allowed Methods

```
GET, PUT, POST, DELETE, PATCH, OPTIONS
```

### Configuration

**Quarkus property**:
```properties
quarkus.http.cors.origins=https://3dime.com,https://www.3dime.com,https://photocalia.com,https://www.photocalia.com
quarkus.http.cors.methods=GET,PUT,POST,DELETE,PATCH
quarkus.http.cors.max-age=86400
```

**Dev mode** (for testing):
```properties
%dev.quarkus.http.cors.origins=/.*/
```

---

## Admin Authentication

Admin endpoints are protected using embedded Quarkus form-based authentication.

### Credentials

| Property | Value | Source |
|----------|-------|--------|
| **Username** | `admin` | Hardcoded |
| **Password** | From env var | `ADMIN_PASSWORD` |
| **Hash** | bcrypt (SHA-512 fallback) | `quarkus.security.embedded-file.plain-text=false` |

### Endpoints Protected

```
GET /users/*
PATCH /users/*
DELETE /users/*
GET /api-docs
GET /api-schema
GET /
```

### Login Flow

1. User navigates to `/login.html`
2. Submits form to `POST /j_security_check` with `j_username` and `j_password`
3. Quarkus validates credentials
4. On success: Sets session cookie, redirects to `/users`
5. On failure: Redirects to `/login-error.html`

### Session Duration

Default: 30 minutes of inactivity. Session cookie is `HttpOnly` and `Secure`.

---

## Vulnerability Scanning

Dependencies are scanned for known CVEs using OWASP Dependency-Check.

### Maven Plugin Configuration

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>11.1.1</version>
</plugin>
```

### Scanning

```bash
mvn dependency-check:check
```

Scans all transitive dependencies and reports any known vulnerabilities. CI/CD should fail the build on HIGH or CRITICAL vulnerabilities.

---

## Environment Variables (Security-Related)

| Variable | Purpose | Example |
|----------|---------|---------|
| `ADMIN_PASSWORD` | Admin user password | `changeme` (should be bcrypt hash in production) |
| `AUTH_SESSION_ENCRYPTION_KEY` | Session cookie encryption key | `aes256-32-byte-key` (≥16 chars, hashed to 32 bytes) |
| `GEMINI_API_KEY` | Service account for Firebase (contains secret) | Full JSON service account key |
| `NOTION_TOKEN` | Notion integration token | `secret_...` |

**⚠️ Important**: Never commit `.env` files or secrets to version control. Use Google Cloud Secret Manager in production.

---

## Best Practices

1. **Token Refresh**: Firebase Admin SDK automatically handles token key rotation. No manual refresh needed.
2. **HTTPS Only**: All production deployments use HTTPS. CSP and HSTS ensure browsers never attempt HTTP.
3. **Least Privilege**: Rate limits and CORS restrict which clients can access which endpoints.
4. **Input Validation**: All file uploads validated by magic bytes before processing.
5. **Audit Logging**: All API requests logged with user identity, endpoint, and status code.
6. **Dependency Updates**: Run `mvn dependency-check:check` regularly to catch CVEs early.
7. **Secrets Rotation**: Periodically rotate `ADMIN_PASSWORD`, `AUTH_SESSION_ENCRYPTION_KEY`, and API tokens.

---

## Monitoring Security

### Health Checks

Liveness and readiness probes at `/health` include dependency checks for Firestore, Gemini, Notion, and GitHub APIs.

```bash
curl http://localhost:8080/health
```

### Logs

All security-relevant events are logged:
- Authentication failures (invalid tokens, bad passwords)
- Rate limit exceeded (HTTP 429)
- File validation failures
- External service errors

In production, logs are sent to Google Cloud Logging as JSON with `severity=WARN` or `severity=ERROR`.

### Metrics

OpenTelemetry traces are exported to Google Cloud Trace for latency and error monitoring.
