# Module: Auth

**Package:** `com.cenicast.lis.auth`

**What it does:** Manages the complete authentication lifecycle — login, JWT + refresh token issuance, token rotation, reuse detection, and logout.

**Who uses it:** All users (every role must log in). No role restrictions on auth endpoints themselves.

**Endpoints:** `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`

---

## Login Flow — `POST /api/v1/auth/login`

### What the client sends

```json
{
  "email": "labadmin@demo.com",
  "password": "ChangeMe123!",
  "tenantSlug": "demo-lab"
}
```

Omit `tenantSlug` (or send `null`) for SUPER_ADMIN login.

### What happens inside `AuthService.login()`

**File:** `auth/service/AuthService.java`

1. **Resolve the user** — two code paths:
   - `tenantSlug == null` → SUPER_ADMIN path → `UserRepository.findByEmailAndTenantIdIsNull(email)`
   - `tenantSlug != null` → Lab user path → find the tenant by slug (404 if not found), then `UserRepository.findByEmailAndTenantId(email, tenant.id)`

2. **Verify password** — `PasswordEncoder.matches(rawPassword, user.passwordHash)`. BCrypt(12) hash comparison. If the user doesn't exist OR the password doesn't match, we treat both the same (to prevent user enumeration attacks). We always:
   - Record a `FAILED_LOGIN` audit event (using `REQUIRES_NEW` — commits even if the outer transaction rolls back)
   - Throw `ApiException(401, "Invalid credentials")`

   For the audit event when the user email doesn't exist, we use `new UUID(0, 0)` (all-zeros UUID) as the `actor_id` — because the `audit_events.actor_id` column is NOT NULL, but there's no real user to reference.

3. **Check if account is active** — if `user.active == false`, throw `ApiException(401, "Account is inactive")`

4. **Issue refresh token**:
   ```
   rawToken  = UUID.randomUUID().toString()   → given to client (in cookie)
   tokenHash = SHA-256(rawToken)              → stored in DB
   ```
   Save a `RefreshToken` entity with:
   - `tokenHash`
   - `userId`, `tenantId`
   - `familyId = UUID.randomUUID()` (new family for this login session)
   - `expiresAt = now + 30 days`
   - `revoked = false`
   - `ipAddress` from request

5. **Issue access token** — `JwtService.generateAccessToken(UserPrincipal)` → signed JWT with 15-min expiry

6. **Audit** — `AuditService.recordAuth(LOGIN, userId, email, tenantId, ipAddress)`

7. **Return** — `LoginResult(accessToken, rawToken, userInfo)`

### What the client receives

**HTTP Response:**
```
200 OK
Set-Cookie: refresh_token=<raw-uuid>; Path=/api/v1/auth; HttpOnly; SameSite=Lax; Max-Age=2592000
Content-Type: application/json
```

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

The `accessToken` is used in `Authorization: Bearer` headers. The `refresh_token` cookie is httpOnly and handled automatically by the browser/HTTP client.

---

## Refresh Flow — `POST /api/v1/auth/refresh`

No Authorization header needed — only the refresh cookie.

### What happens inside `AuthService.refresh()`

**File:** `auth/service/AuthService.java`
**Annotation:** `@Transactional(noRollbackFor = ApiException.class)`

The `noRollbackFor` is critical: if we throw an `ApiException` (e.g., 401 Unauthorized) after calling `revokeFamily()`, Spring would normally roll back the entire transaction — including the revocation. `noRollbackFor = ApiException.class` tells Spring to commit even when this exception is thrown, so the family revocation always persists.

**Step by step:**

1. **Null/blank cookie** → throw `ApiException(401, "Missing refresh token")`

2. **Hash the raw token** — `SHA-256(rawCookieToken)` → look up in DB by `token_hash`

3. **Not found** → throw `ApiException(401, "Invalid refresh token")`

4. **Reuse detection** — if `existing.revoked == true`:
   - Call `refreshTokenRepository.revokeFamily(existing.familyId)` — marks ALL tokens in this family as revoked
   - Load the victim user's email: `userRepository.findById(existing.userId).map(User::getEmail).orElse("unknown")`
   - Record `TOKEN_REVOKED` audit event
   - Throw `ApiException(401, "Refresh token reuse detected — all sessions invalidated")`
   - **Why this is a breach signal**: The legitimate holder of the token already rotated it. If someone is presenting the old (revoked) token, they got access to a token that was supposed to be gone. Revoking the entire family ensures the attacker's new token (from a prior rotation) is also dead.

5. **Expired check** — if `now > existing.expiresAt` → throw `ApiException(401, "Refresh token expired")`. Note: expired is NOT the same as reused — we don't revoke the family for normal expiration.

6. **Rotate** — `existing.revoked = true`

7. **Load the user** — `userRepository.findById(existing.userId)` — 401 if user deleted

8. **Issue new refresh token** — same `familyId` as old; new `tokenHash`, new `id`, new `expiresAt`

9. **Link** — `existing.replacedBy = newToken.id` (forms the rotation audit chain)

10. **Issue new access token** — `JwtService.generateAccessToken(user)`

11. **Audit** — `AuditService.recordAuth(TOKEN_REFRESH, ...)`

12. **Return** — `RefreshResult(newAccessToken, newRawToken)`

### What the client receives

```
200 OK
Set-Cookie: refresh_token=<new-raw-uuid>; ...
```

```json
{
  "accessToken": "eyJhbGci...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

---

## Logout Flow — `POST /api/v1/auth/logout`

**Requires:** `Authorization: Bearer <accessToken>` header.

**`@PreAuthorize("isAuthenticated()")` on the controller method** — the user must be authenticated. Note: because `JwtAuthFilter` processes `/api/v1/auth/logout` (it is NOT in the `shouldNotFilter` skip list), the Bearer token is validated and the user is authenticated before reaching the controller.

### What happens inside `AuthService.logout()`

1. **Null/blank cookie** — if there's no refresh cookie (e.g., browser already cleared it), just record `LOGOUT` audit event and return — nothing to revoke

2. **Hash the raw token** — look up in DB by `token_hash`

3. **Revoke** — if found and `!token.revoked`, set `revoked = true` and save

4. **Audit** — `AuditService.recordAuth(LOGOUT, principal.userId, principal.email, principal.tenantId, ipAddress)`

### What the client receives

```
204 No Content
Set-Cookie: refresh_token=; Path=/api/v1/auth; HttpOnly; Max-Age=0
```

`Max-Age=0` tells the browser to delete the cookie immediately.

---

## DTOs

### `LoginRequest`

**File:** `auth/dto/LoginRequest.java`

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `email` | String | `@NotBlank @Email` | User's email address |
| `password` | String | `@NotBlank` | Raw password (never stored; matched against hash) |
| `tenantSlug` | String | None | Null = SUPER_ADMIN. Must match a tenant's slug otherwise. |

### `LoginResponse`

**File:** `auth/dto/LoginResponse.java`

| Field | Type | Notes |
|---|---|---|
| `accessToken` | String | The JWT. Store in memory only — never in localStorage. |
| `tokenType` | String | Always `"Bearer"` |
| `expiresIn` | long | Always `900` (seconds = 15 minutes) |
| `user` | UserInfo | User details embedded in response |

### `UserInfo`

**File:** `auth/dto/UserInfo.java`

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | User's database ID |
| `email` | String | |
| `firstName` | String | |
| `lastName` | String | |
| `role` | String | e.g., `"LAB_ADMIN"` |
| `tenantId` | UUID | Null for SUPER_ADMIN |

### `RefreshResponse`

**File:** `auth/dto/RefreshResponse.java`

| Field | Type | Notes |
|---|---|---|
| `accessToken` | String | New JWT |
| `tokenType` | String | Always `"Bearer"` |
| `expiresIn` | long | Always `900` |

---

## `RefreshToken` Entity

**File:** `auth/model/RefreshToken.java`

| Field | Column | Type | Notes |
|---|---|---|---|
| `id` | `id` | UUID | PK, generated in `@PrePersist` |
| `tokenHash` | `token_hash` | String (64 chars) | SHA-256 hex of the raw token. Raw token is never stored. |
| `userId` | `user_id` | UUID | Who owns this token. No `@ManyToOne` — UUID stored directly for module decoupling. |
| `tenantId` | `tenant_id` | UUID (nullable) | Denormalized from user. Null for SUPER_ADMIN. |
| `expiresAt` | `expires_at` | Instant | 30 days from issuance |
| `revoked` | `revoked` | boolean | `true` = cannot be used |
| `familyId` | `family_id` | UUID | Shared by all tokens from the same login session |
| `replacedBy` | `replaced_by` | UUID (nullable) | ID of the next token in the rotation chain |
| `ipAddress` | `ip_address` | String (nullable) | Client IP at token issuance |
| `createdAt` | `created_at` | Instant | Auto-set in `@PrePersist` |

**Why no `@ManyToOne User`?** Keeping `userId` as a plain UUID (instead of a JPA relationship) avoids a dependency from the `auth` module to the `users` module at the ORM level. It keeps the modules more decoupled.

---

## Key Classes Summary

| Class | File | Responsibility |
|---|---|---|
| `AuthController` | `auth/controller/AuthController.java` | HTTP endpoints, cookie building, IP extraction |
| `AuthService` | `auth/service/AuthService.java` | Business logic: login, refresh, logout, SHA-256 hashing |
| `RefreshToken` | `auth/model/RefreshToken.java` | JPA entity for refresh token storage |
| `RefreshTokenRepository` | `auth/repository/RefreshTokenRepository.java` | `findByTokenHash()`, `revokeFamily()` |
| `LoginRequest` | `auth/dto/LoginRequest.java` | Validated request body for login |
| `LoginResponse` | `auth/dto/LoginResponse.java` | Response body for login |
| `RefreshResponse` | `auth/dto/RefreshResponse.java` | Response body for token refresh |
| `UserInfo` | `auth/dto/UserInfo.java` | User info embedded in login response |
| `JwtService` | `common/security/JwtService.java` | JWT generation and validation |
| `JwtAuthFilter` | `common/security/JwtAuthFilter.java` | Per-request JWT extraction and principal setup |
| `AuditService` | `common/audit/AuditService.java` | Records LOGIN, FAILED_LOGIN, LOGOUT, TOKEN_REFRESH, TOKEN_REVOKED |
