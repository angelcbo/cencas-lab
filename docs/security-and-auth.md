# Security & Authentication

This document explains every security mechanism in the application, from first principles. If you are new to any of these concepts, follow the reference links — they are carefully chosen to be beginner-friendly.

---

## Authentication vs. Authorization

Before diving in, two terms to understand:

- **Authentication** — *Who are you?* Verifying identity. (Login with email + password → you are "labadmin@demo.com".)
- **Authorization** — *What can you do?* Checking permissions. (You're authenticated as LAB_ADMIN → you can list users, but not create tenants.)

> Reference: [Okta — Authentication vs Authorization](https://www.okta.com/identity-101/authentication-vs-authorization/)

We use **JWT tokens** for authentication and **@PreAuthorize with roles** for authorization.

---

## JWT (JSON Web Tokens)

### What is a JWT?

A JWT is a compact, URL-safe string that carries a set of **claims** (pieces of data) about the user. It has three parts separated by dots:

```
header.payload.signature
```

- **Header**: algorithm used to sign the token (we use `HS256` — HMAC with SHA-256)
- **Payload**: the claims — your user ID, role, tenant, etc. (base64url-encoded, NOT encrypted — don't put secrets here)
- **Signature**: proves the token hasn't been tampered with. Created by signing `header + "." + payload` with our secret key.

You can decode any JWT at [jwt.io](https://jwt.io/) — paste one in to see the claims.

> References: [JWT Introduction (jwt.io)](https://jwt.io/introduction), [RFC 7519 — JSON Web Token](https://www.rfc-editor.org/rfc/rfc7519)

### Why JWTs are stateless

The server does **not** need to look up a database record to validate a JWT. It only needs to:
1. Verify the signature matches (proves it was issued by us and not tampered with)
2. Check the `exp` claim (expiration time)

This means every server in a load-balanced cluster can validate tokens independently — no shared session store needed.

### Our JWT claims

Every access token we issue contains exactly these claims:

| Claim key | Example value | Type | Notes |
|---|---|---|---|
| `sub` | `"550e8400-e29b-41d4-a716-446655440000"` | String (UUID) | User ID — the subject of the token |
| `tenantId` | `"a1b2c3d4-..."` | String (UUID) or null | Null for SUPER_ADMIN (no tenant) |
| `role` | `"LAB_ADMIN"` | String | One of the 5 roles |
| `email` | `"labadmin@demo.com"` | String | For logging/audit — not for authorization |
| `jti` | `"f7e3a1b2-..."` | String (UUID) | Unique token ID; enables denylist if needed |
| `iat` | `1741320000` | Unix timestamp | Issued at time |
| `exp` | `1741320900` | Unix timestamp | Expiration (iat + 900 seconds = 15 minutes) |

**Why include `tenantId` in the token?**
So the [Hibernate tenant filter](./multitenancy.md) can fire on every request without a database lookup. The filter needs to know the current user's tenant ID, and the JWT carries it.

**Why include `role`?**
So `@PreAuthorize("hasRole('LAB_ADMIN')")` can evaluate without a database lookup. The role is embedded in the token.

**Why 15-minute expiry?**
Short-lived tokens limit the damage if a token is stolen. After 15 minutes it's worthless. The refresh token (explained below) handles long-term sessions without requiring frequent logins.

### Class: `JwtService`

**File:** `backend/src/main/java/com/cenicast/lis/common/security/JwtService.java`

| Method | What it does |
|---|---|
| `generateAccessToken(UserPrincipal)` | Builds a signed JWT with all claims. Returns the compact token string. |
| `validateAndExtract(String token)` | Verifies signature + expiry. Returns `Claims` on success. Throws `ApiException(401)` on failure. |
| `extractUserId(Claims)` | Parses `sub` claim as UUID |
| `extractTenantId(Claims)` | Parses `tenantId` claim as UUID — returns `null` for SUPER_ADMIN |
| `extractRole(Claims)` | Reads `role` claim string |
| `extractEmail(Claims)` | Reads `email` claim string |

The signing key is derived from `app.jwt.secret` in `application.yml`. It must be at least 32 characters for HS256. In production, set the `JWT_SECRET` environment variable to a cryptographically random 64-character string.

> Reference: [JJWT library — reading a JWT](https://github.com/jwtk/jjwt#reading-a-jwt)

---

## Refresh Tokens

### The problem

JWTs expire in 15 minutes. If we only used JWTs, users would need to log in every 15 minutes — terrible user experience.

### The solution: opaque refresh tokens

When a user logs in, we issue **two things**:
1. A short-lived **access token** (JWT, 15 min) — used in `Authorization: Bearer` headers
2. A long-lived **refresh token** (opaque UUID, 30 days) — stored in an httpOnly cookie

When the access token expires, the frontend silently calls `POST /api/v1/auth/refresh`. The server validates the refresh cookie, issues a new access token and a new refresh token, and the user never sees a login screen.

### What "opaque" means

The refresh token is just a random UUID: `"f7c3a1b2-e29b-41d4-a716-446655440001"`. It has no structure, no claims, no signature. The server looks it up in the database to validate it.

### Why the refresh token is stored hashed in the database

We store a **SHA-256 hex hash** of the raw token in the `refresh_tokens.token_hash` column. The raw token is only ever seen by the client.

Why? If the database is breached, the attacker gets the hashes, not the raw tokens. SHA-256 is a one-way function — you cannot reverse a hash to get the original token. This is the same principle as hashing passwords.

```java
// In AuthService.java
String rawToken = UUID.randomUUID().toString();         // given to the client
String tokenHash = sha256Hex(rawToken);                 // stored in DB
```

### Token rotation

Every time the refresh token is used, it is **rotated**:

1. Client sends old refresh cookie → `POST /api/v1/auth/refresh`
2. Server finds the token row by hash
3. Server marks old token as `revoked = true`
4. Server creates a new token row (same `family_id`)
5. Server sets `old.replaced_by = new.id` (forms an audit chain)
6. Server returns new access token + new refresh cookie
7. Client uses the new cookie going forward

The old token can never be used again.

### Reuse detection (security breach signal)

All tokens from the same login session share a `family_id`. If a **revoked** token is ever presented:

1. Someone has a token that was already rotated — this signals the raw token may have been stolen
2. We immediately **revoke the entire family** (`UPDATE refresh_tokens SET revoked=true WHERE family_id = ?`)
3. All active sessions for this login chain are invalidated
4. The user must log in again
5. A `TOKEN_REVOKED` audit event is recorded

> Reference: [Auth0 — Refresh Tokens: What Are They and When to Use Them](https://auth0.com/blog/refresh-tokens-what-are-they-and-when-to-use-them/)

### Class: `RefreshToken`

**File:** `backend/src/main/java/com/cenicast/lis/auth/model/RefreshToken.java`

Key fields:
- `tokenHash` — SHA-256 hex (64 chars). The raw UUID is never stored.
- `familyId` — UUID shared by all tokens from the same login session
- `replacedBy` — UUID pointing to the next token in the rotation chain (nullable)
- `revoked` — boolean. `true` = this token is dead
- `expiresAt` — 30 days from issuance

---

## Cookie Security

### What is an httpOnly cookie?

An httpOnly cookie is a cookie that the browser will **never expose to JavaScript**. `document.cookie` does not include httpOnly cookies. This is critical for security:

- If an attacker injects malicious JavaScript into your page (XSS attack), they cannot steal the refresh token because it's in an httpOnly cookie
- The browser automatically includes the cookie in requests to the matching domain/path, so the frontend code doesn't need to handle it at all

> Reference: [MDN — Cookies: Restrict access to cookies](https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#restrict_access_to_cookies)

### Our cookie configuration

| Attribute | Dev value | Prod value | Meaning |
|---|---|---|---|
| `HttpOnly` | `true` | `true` | JavaScript cannot read this cookie |
| `Secure` | `false` | `true` | In prod: only sent over HTTPS connections |
| `SameSite` | `Lax` | `Strict` | Controls cross-site sending. `Strict` = never sent cross-site. |
| `Path` | `/api/v1/auth` | `/api/v1/auth` | Cookie only sent to auth endpoints |
| `Domain` | `localhost` | `app.cenicast.com` | Which domain the cookie belongs to |
| `Max-Age` | 30 days | 30 days | Cookie lifetime in seconds |

**Why `Path=/api/v1/auth`?** The refresh cookie is only needed by the auth endpoints (refresh, logout). Setting the path prevents the browser from sending it on every API call (e.g., `GET /api/v1/users`) — least privilege principle for cookies.

### Cookie clearing on logout

Logout sends a new `Set-Cookie` header with `Max-Age=0`:
```
Set-Cookie: refresh_token=; Path=/api/v1/auth; HttpOnly; Max-Age=0
```
`Max-Age=0` instructs the browser to delete the cookie immediately.

---

## Password Hashing with BCrypt

### Why we hash passwords

Passwords are never stored in plain text. If the database is breached, we don't want attackers to have users' passwords (users reuse passwords across sites).

### Why BCrypt and not MD5/SHA-256?

MD5 and SHA-256 are **fast** hash functions. "Fast" is bad for passwords because attackers can try billions of guesses per second using GPUs. BCrypt is intentionally **slow** — it has a cost factor (work factor) that controls how many iterations of the algorithm run.

BCrypt also automatically **salts** passwords — it mixes a random value into each hash before hashing. This means two users with the same password will have different hashes, preventing rainbow table attacks.

> Reference: [Auth0 — BCrypt: Hashing in Action](https://auth0.com/blog/hashing-in-action-understanding-bcrypt/)

### Our setting: strength 12

```java
// SecurityConfig.java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}
```

Strength 12 means 2¹² = 4,096 iterations. On modern hardware this takes roughly 200–400ms per hash — acceptable for login (one hash), but makes brute-force attacks impractical (attacker can only try ~2–5 passwords per second per CPU core).

---

## Spring Security Filter Chain

### What is a servlet filter?

A servlet filter is a piece of code that runs on **every HTTP request** before it reaches your controller. Filters can inspect and modify the request, reject it outright (returning an error response), or pass it along to the next filter in the chain.

Spring Security is implemented as a chain of filters. Our custom filters are added to this chain.

> Reference: [Spring Security — Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)

### Our filters in order

#### 1. `CorrelationIdFilter`

**File:** `common/util/CorrelationIdFilter.java`

Runs first. Reads the `X-Correlation-Id` request header (or generates a new UUID if absent). Puts it in the **MDC** (Mapped Diagnostic Context — a thread-local key/value store used by the logging framework). Sets the same value as an `X-Correlation-Id` response header.

This means every log line for a request contains the correlation ID, making it easy to trace a request across all log entries.

#### 2. `JwtAuthFilter`

**File:** `common/security/JwtAuthFilter.java`

Extends `OncePerRequestFilter` — Spring guarantees this filter runs exactly once per request, even for request dispatching scenarios.

**`shouldNotFilter()` method:** Certain paths skip JWT processing entirely because they don't require authentication:
- `/api/v1/auth/login` — the user is not logged in yet
- `/api/v1/auth/refresh` — uses a cookie, not a Bearer token
- `/actuator/*`, `/api/v1/docs*`, `/api/v1/api-docs*` — public endpoints

> **Important:** `/api/v1/auth/logout` is **NOT** in the skip list. Logout requires a valid Bearer token (the user must be authenticated to log out). If you skip JWT processing for logout, `@PreAuthorize("isAuthenticated()")` will fail with 403.

**`doFilterInternal()` method — step by step:**

1. Read `Authorization` header. If absent or doesn't start with `"Bearer "`, skip to next filter (Spring Security will reject the request if it needs authentication).
2. Extract the token: `header.substring(7)`
3. Call `jwtService.validateAndExtract(token)`:
   - If valid: returns `Claims` (the token's payload)
   - If invalid/expired: throws `ApiException(401)` — caught here, we proceed without authentication (Spring Security will reject later)
4. Build `UserPrincipal` from claims — **no database lookup**. The token is self-contained.
5. Call `TenantContextHolder.set(principal.getTenantId())` — stores the tenant ID for the Hibernate filter
6. Put the principal in `SecurityContextHolder` — this is how Spring Security knows the user is authenticated
7. Call `chain.doFilter()` — proceed to the next filter
8. `finally` block: **always** call `TenantContextHolder.clear()` — even if an exception occurs. This prevents the ThreadLocal from leaking to the next request if the thread is reused by the thread pool.

### `SecurityContextHolder`

This is how Spring Security tracks the currently authenticated user within a request. It's thread-bound (backed by a `ThreadLocal<SecurityContext>`). Once `JwtAuthFilter` sets the authentication, any downstream code (controllers, services) can get it via `@AuthenticationPrincipal` or `SecurityContextHolder.getContext().getAuthentication()`.

---

## Role-Based Access Control (RBAC)

### The five roles

| Role | Scope | What they can do |
|---|---|---|
| `SUPER_ADMIN` | Platform-wide | Manage tenants, create first LAB_ADMIN. Has no tenant of their own. |
| `LAB_ADMIN` | Own tenant | All operations within their tenant (users, catalog, patients, orders, results, billing) |
| `LAB_RECEPTIONIST` | Own tenant | Create patients and orders, record payments |
| `LAB_ANALYST` | Own tenant | Enter and validate results; read samples and orders |
| `LAB_DOCTOR` | Own tenant | Read-only access to assigned patients' results and PDFs |

Roles are defined in `users/model/Role.java` (a Java enum).

### How `@PreAuthorize` works

`@EnableMethodSecurity` in `SecurityConfig.java` enables Spring Security's method-level security. This allows `@PreAuthorize` annotations on controller methods:

```java
// UserController.java
@GetMapping
@PreAuthorize("hasRole('LAB_ADMIN')")
public Page<UserResponse> listUsers(Pageable pageable) { ... }
```

When this method is called, Spring evaluates the expression `hasRole('LAB_ADMIN')` against the current user's authorities. If it fails, Spring throws `AccessDeniedException`, which our `GlobalExceptionHandler` maps to HTTP 403.

> Reference: [Spring Security — Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)

### The `ROLE_` prefix convention

Spring Security stores authorities as strings with a `ROLE_` prefix. Our `UserPrincipal.getAuthorities()` method does this automatically:

```java
// UserPrincipal.java
@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    // role = "LAB_ADMIN" → authority = "ROLE_LAB_ADMIN"
}
```

In `@PreAuthorize`, `hasRole('LAB_ADMIN')` automatically prepends `ROLE_`, so it checks for `"ROLE_LAB_ADMIN"`. This is why you write `hasRole('LAB_ADMIN')` (no prefix) in annotations but see `ROLE_LAB_ADMIN` in the actual authority list.

### Class: `UserPrincipal`

**File:** `common/security/UserPrincipal.java`

This class implements Spring Security's `UserDetails` interface. It is **stateless** — rebuilt from JWT claims on every request, never from the database. This keeps authentication O(1) without any DB lookups.

Fields:
- `userId` — UUID from `sub` claim
- `tenantId` — UUID from `tenantId` claim (null for SUPER_ADMIN)
- `email` — from `email` claim
- `passwordHash` — empty string `""` (we don't need the hash after login; it's only used in `UserService.changePassword()`)
- `role` — from `role` claim
- `active` — hardcoded `true` (we trust the JWT; account status is checked at login time)

---

## Error Responses

All authentication and authorization errors return a standard JSON body. See [API Conventions](./api-conventions.md) for the full error response shape.

| HTTP Status | When | Example message |
|---|---|---|
| 400 | Validation failure (missing/invalid field) | `"email: must not be blank"` |
| 401 | No token, expired token, invalid token, wrong password | `"Invalid or expired token"` |
| 403 | Valid token but insufficient role | `"Access denied"` |
| 500 | Unexpected server error | `"An unexpected error occurred"` |

### `GlobalExceptionHandler`

**File:** `common/exception/GlobalExceptionHandler.java`

This `@RestControllerAdvice` class intercepts exceptions thrown anywhere in a controller or service and converts them to proper HTTP responses.

**Why does `AccessDeniedException` need its own `@ExceptionHandler`?**

Spring Security's `AccessDeniedException` is a `RuntimeException`. If we only had `@ExceptionHandler(Exception.class)`, Spring would catch it there and return 500 — which is wrong. The explicit `@ExceptionHandler(AccessDeniedException.class)` takes priority and returns 403 correctly. The same applies to `AuthenticationException` → 401.

**The five handlers:**

| Exception class | HTTP status | Notes |
|---|---|---|
| `ApiException` | From `exception.getStatus()` | Our custom business logic exceptions |
| `MethodArgumentNotValidException` | 400 | Bean Validation failures; field names included in message |
| `AccessDeniedException` | 403 | Spring Security role check failure |
| `AuthenticationException` | 401 | Spring Security authentication failure |
| `Exception` (catch-all) | 500 | Logged at ERROR level with correlation ID |
