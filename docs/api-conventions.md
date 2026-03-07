# API Conventions

This document describes the conventions that apply to every API endpoint in the application. Follow these when building new endpoints.

---

## Base URL

| Environment | Base URL |
|---|---|
| Local development | `http://localhost:8080/api/v1` |
| Production | `https://app.cenicast.com/api/v1` |

All API endpoints are prefixed with `/api/v1/`. The `/v1/` allows us to introduce breaking changes in a future `/v2/` without breaking existing clients.

---

## Authentication

Most endpoints require authentication. Pass the access token in the `Authorization` header:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

Where to get a token: `POST /api/v1/auth/login` → response body contains `accessToken`.

Access tokens expire in **15 minutes**. Use `POST /api/v1/auth/refresh` (with the httpOnly cookie) to get a new one silently.

---

## Swagger UI

**URL:** http://localhost:8080/api/v1/docs

The Swagger UI auto-generates interactive documentation from the source code annotations. You can:
- Browse all endpoints with full request/response schemas
- Try requests directly in the browser
- See validation constraints and example values

**To authenticate in Swagger UI:**
1. Log in via `POST /api/v1/auth/login` (you can do this right in Swagger)
2. Copy the `accessToken` from the response
3. Click the **Authorize** button (lock icon, top right of Swagger UI)
4. Enter: `Bearer <your-accessToken>` (include the word "Bearer")
5. Click **Authorize** — subsequent requests will include the header automatically

The raw OpenAPI JSON spec is available at: http://localhost:8080/api/v1/api-docs

---

## Request & Response Format

- All request bodies are **JSON** with `Content-Type: application/json`
- All successful response bodies are **JSON**
- All timestamps in responses are **ISO-8601 UTC** (e.g., `"2026-03-07T04:00:00Z"`)
- All UUIDs are lowercase with hyphens (e.g., `"550e8400-e29b-41d4-a716-446655440000"`)

---

## Pagination

Endpoints that return lists use Spring's `Pageable` system. Pass query parameters:

| Parameter | Default | Description |
|---|---|---|
| `page` | `0` | Page number (zero-indexed) |
| `size` | `20` | Items per page |
| `sort` | (none) | Sort field and direction, e.g. `createdAt,desc` |

Example: `GET /api/v1/users?page=0&size=10&sort=createdAt,desc`

**Paginated response shape:**

```json
{
  "content": [ ... ],
  "totalElements": 47,
  "totalPages": 5,
  "size": 10,
  "number": 0,
  "first": true,
  "last": false,
  "numberOfElements": 10,
  "empty": false
}
```

---

## Error Response Shape

All errors return the same JSON structure, regardless of which endpoint or what went wrong:

```json
{
  "timestamp": "2026-03-07T04:00:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "User not found",
  "path": "/api/v1/users/550e8400-e29b-41d4-a716-446655440000",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

| Field | Description |
|---|---|
| `timestamp` | UTC ISO-8601 timestamp of when the error occurred |
| `status` | HTTP status code (integer) |
| `error` | HTTP status reason phrase (string) |
| `message` | Human-readable error description |
| `path` | The request URI that caused the error |
| `correlationId` | Request tracing ID — include this when reporting bugs |

The `correlationId` matches the `X-Correlation-Id` header on the response. Search your logs for this ID to find all log lines from that request.

---

## HTTP Status Codes

| Code | Name | When used | Example |
|---|---|---|---|
| `200` | OK | Successful GET, PUT, PATCH | User retrieved, user updated |
| `201` | Created | Successful POST that creates a resource | Tenant created, user created |
| `204` | No Content | Successful DELETE or action with no body | User deactivated, logout |
| `400` | Bad Request | Input validation failure | Missing required field, invalid email format |
| `401` | Unauthorized | Not authenticated | No token, expired token, wrong password |
| `403` | Forbidden | Authenticated but lacking permission | LAB_ADMIN calling SUPER_ADMIN endpoint |
| `404` | Not Found | Resource not found (or filtered out by tenant) | User ID doesn't exist or belongs to another tenant |
| `409` | Conflict | Duplicate unique value | Tenant slug already exists, email already in use |
| `422` | Unprocessable Entity | Business rule violation | Creating a SUPER_ADMIN user via the user endpoint |
| `500` | Internal Server Error | Unexpected bug | Should never happen in normal operation |

---

## Validation Errors (400)

When request body validation fails, the error message includes details for each invalid field:

```json
{
  "timestamp": "2026-03-07T04:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "email: must not be blank; password: size must be between 8 and 100",
  "path": "/api/v1/auth/login",
  "correlationId": "..."
}
```

Multiple field errors are joined with `"; "`.

---

## Correlation IDs

Every request gets a correlation ID — a UUID used to trace the request through all log entries.

**Inbound:** Include `X-Correlation-Id: <your-uuid>` in your request to use your own ID (useful for client-side tracing).

**Outbound:** The server always sets `X-Correlation-Id` in the response header, and includes `correlationId` in error response bodies.

**Usage in logs:** To trace a specific request:
```bash
docker logs cenicast-backend 2>&1 | grep "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

---

## All Current Endpoints

### Auth — `POST /api/v1/auth/...`

| Method | Path | Auth | Role | Body | Response | Description |
|---|---|---|---|---|---|---|
| POST | `/api/v1/auth/login` | None | — | `LoginRequest` | `LoginResponse` + cookie | Authenticate and get tokens |
| POST | `/api/v1/auth/refresh` | Cookie | — | None | `RefreshResponse` + new cookie | Rotate refresh token |
| POST | `/api/v1/auth/logout` | Bearer | Authenticated | None | 204 | Revoke refresh token and clear cookie |

**`LoginRequest`:**
```json
{
  "email": "labadmin@demo.com",
  "password": "ChangeMe123!",
  "tenantSlug": "demo-lab"
}
```
Omit `tenantSlug` (or pass `null`) for SUPER_ADMIN login.

**`LoginResponse`:**
```json
{
  "accessToken": "eyJhbGci...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "550e8400-...",
    "email": "labadmin@demo.com",
    "firstName": "Lab",
    "lastName": "Admin",
    "role": "LAB_ADMIN",
    "tenantId": "a1b2c3d4-..."
  }
}
```

---

### Tenants — `GET/POST/PUT/DELETE /api/v1/tenants/...`

All tenant endpoints require `SUPER_ADMIN` role.

| Method | Path | Body | Response | Description |
|---|---|---|---|---|
| GET | `/api/v1/tenants` | — | Paginated `TenantResponse` | List all tenants |
| POST | `/api/v1/tenants` | `TenantRequest` | `TenantResponse` (201) | Create a new tenant |
| GET | `/api/v1/tenants/{id}` | — | `TenantResponse` | Get a tenant by ID |
| PUT | `/api/v1/tenants/{id}` | `TenantRequest` | `TenantResponse` | Update a tenant |
| DELETE | `/api/v1/tenants/{id}` | — | 204 | Soft-deactivate a tenant |
| POST | `/api/v1/tenants/{tenantId}/users` | `CreateUserRequest` | `UserResponse` (201) | Create first LAB_ADMIN for a tenant |

**`TenantRequest`:**
```json
{
  "slug": "my-lab",
  "name": "My Clinical Lab",
  "timezone": "America/Mexico_City",
  "taxRate": 0.1600
}
```
`timezone` and `taxRate` are optional (defaults: `"America/Mexico_City"` and `0.1600`).

**`TenantResponse`:**
```json
{
  "id": "550e8400-...",
  "slug": "my-lab",
  "name": "My Clinical Lab",
  "timezone": "America/Mexico_City",
  "taxRate": 0.16,
  "active": true,
  "createdAt": "2026-03-07T04:00:00Z"
}
```

---

### Users — `GET/POST/PUT /api/v1/users/...`

| Method | Path | Auth | Role | Body | Response | Description |
|---|---|---|---|---|---|---|
| GET | `/api/v1/users` | Bearer | LAB_ADMIN | — | Paginated `UserResponse` | List users in own tenant |
| POST | `/api/v1/users` | Bearer | LAB_ADMIN | `CreateUserRequest` | `UserResponse` (201) | Create user in own tenant |
| GET | `/api/v1/users/{id}` | Bearer | LAB_ADMIN, LAB_RECEPTIONIST | — | `UserResponse` | Get user by ID |
| PUT | `/api/v1/users/{id}` | Bearer | LAB_ADMIN | `UpdateUserRequest` | `UserResponse` | Update user |
| PUT | `/api/v1/users/{id}/password` | Bearer | Authenticated (self only) | `ChangePasswordRequest` | 204 | Change own password |

**`CreateUserRequest`:**
```json
{
  "email": "analyst@demo.com",
  "password": "SecurePass123!",
  "firstName": "Ana",
  "lastName": "López",
  "role": "LAB_ANALYST"
}
```
Valid roles: `LAB_ADMIN`, `LAB_RECEPTIONIST`, `LAB_ANALYST`, `LAB_DOCTOR`. Cannot create `SUPER_ADMIN` via this endpoint.

**`UpdateUserRequest`** (all fields optional — null = no change):
```json
{
  "firstName": "Ana",
  "lastName": "González",
  "role": "LAB_ADMIN",
  "active": false
}
```

**`ChangePasswordRequest`:**
```json
{
  "currentPassword": "OldPass123!",
  "newPassword": "NewSecurePass456!"
}
```

**`UserResponse`:**
```json
{
  "id": "550e8400-...",
  "tenantId": "a1b2c3d4-...",
  "email": "analyst@demo.com",
  "firstName": "Ana",
  "lastName": "López",
  "role": "LAB_ANALYST",
  "active": true,
  "createdAt": "2026-03-07T04:00:00Z"
}
```

---

### Health Check

| Method | Path | Auth | Response |
|---|---|---|---|
| GET | `/actuator/health` | None | `{"status":"UP"}` |

Used by Docker healthcheck and load balancers. No authentication required. Only exposes `status` — no sensitive details.
