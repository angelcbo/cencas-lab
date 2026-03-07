# Module: Users

**Package:** `com.cenicast.lis.users`

**What it does:** Manages user accounts within a tenant. Handles creating, reading, and updating users (all tenant-scoped), plus password changes for any authenticated user.

**Who uses it:**
- `LAB_ADMIN` — full CRUD access within their own tenant
- `LAB_RECEPTIONIST` — can read individual user records (e.g., to display "created by" info)
- Any authenticated user — can change their own password

**Endpoints:** `GET/POST/PUT /api/v1/users/...`

---

## Roles Reference

Every user in the system has exactly one role. Roles are defined in `users/model/Role.java` as a Java `enum`.

| Role | Scope | What they can do |
|---|---|---|
| `SUPER_ADMIN` | Platform-wide | Manage tenants, create first LAB_ADMIN for a tenant. Has no `tenantId`. |
| `LAB_ADMIN` | Own tenant only | Full user management within the tenant. All CRUD on patients, orders, etc. |
| `LAB_RECEPTIONIST` | Own tenant only | Create patients, create orders, record payments. Can read user records. |
| `LAB_ANALYST` | Own tenant only | Enter and validate results. Read samples and orders. |
| `LAB_DOCTOR` | Own tenant only | Read-only: assigned patient results and generated PDFs. |

**Important:** `SUPER_ADMIN` is only created via `DataInitializer` at startup (dev profile). It cannot be created through any API endpoint — attempting to create a `SUPER_ADMIN` via `POST /api/v1/users` returns `422 Unprocessable Entity`.

---

## User CRUD

### List users — `GET /api/v1/users`

**Role required:** `LAB_ADMIN`

Returns a paginated list of all users in the caller's tenant. The Hibernate `tenantFilter` is automatically applied by `TenantFilterAspect` before the repository call, so the query becomes:

```sql
SELECT * FROM users WHERE tenant_id = '<callerTenantId>' ...
```

No extra filtering code is needed in the service — the filter handles it transparently.

**Service:** `UserService.listUsers(Pageable pageable)` — calls `userRepository.findAll(pageable)`.

**Returns:** Paginated `UserResponse`. See [Pagination](../api-conventions.md#pagination) for the response shape.

---

### Get one user — `GET /api/v1/users/{id}`

**Role required:** `LAB_ADMIN` or `LAB_RECEPTIONIST`

**Why two roles?** A receptionist creating an order needs to look up user accounts (e.g., to assign a doctor). LAB_ADMIN needs it for management. Neither role can ever see users from another tenant.

**The `findByIdWithFilter` pattern — why it matters:**

Spring Data JPA's `findById(UUID)` calls `EntityManager.find()` under the hood. In Hibernate 6, this is a direct primary-key lookup that **bypasses session-level `@Filter` annotations**. If we used `findById`, a Tenant A user could look up Tenant B's user UUID and get a result — a data leak.

Our fix: a custom JPQL query in `UserRepository`:

```java
// UserRepository.java
@Query("SELECT u FROM User u WHERE u.id = :id")
Optional<User> findByIdWithFilter(@Param("id") UUID id);
```

JPQL goes through Hibernate's query engine, which **does respect session-level filters**. The effective SQL becomes:

```sql
SELECT * FROM users WHERE id = ? AND tenant_id = '<callerTenantId>'
```

Tenant B's UUID + Tenant A's token → zero rows → 404. Correct behavior.

**Rule:** Never use `findById` on tenant-scoped entities. Always use a custom `@Query`.

**Service:** `UserService.getUser(UUID userId)` — uses `findByIdWithFilter`.

---

### Create a user — `POST /api/v1/users`

**Role required:** `LAB_ADMIN`

**Service:** `UserService.createUser(CreateUserRequest req, UUID tenantId, UserPrincipal actor, String ipAddress)`

The `tenantId` comes from the authenticated principal (`principal.getTenantId()`), NOT from the request body. This means:
- Lab users can only create users in their own tenant — the caller's tenant is enforced at the service layer
- There is no way for a LAB_ADMIN to inject a different tenantId

**What happens:**

1. Reject `SUPER_ADMIN` role — throws `ApiException(422)` (creating a platform admin via a tenant endpoint makes no sense)
2. Check email uniqueness within the tenant: `userRepository.existsByEmailAndTenantId(email, tenantId)` — throws `ApiException(409)` if taken
3. Create the `User` entity:
   - `tenantId` = from principal (not request body)
   - `passwordHash` = `BCrypt(strength=12)` of the raw password — raw password is never stored
   - `active = true`
4. Save to database
5. Audit: `AuditService.recordResource(CREATE_USER, actor, "User", user.id, {email: ..., role: ...}, ipAddress)`
6. Return `UserResponse` with `201 Created` and a `Location` header

**Why SUPER_ADMIN can't use this endpoint:**

`@PreAuthorize("hasRole('LAB_ADMIN')")` blocks SUPER_ADMIN. The SUPER_ADMIN creates the first LAB_ADMIN for a new tenant via `POST /api/v1/tenants/{tenantId}/users` instead (see [Tenant module](tenant.md#creating-the-first-lab_admin)).

---

### Update a user — `PUT /api/v1/users/{id}`

**Role required:** `LAB_ADMIN`

**Service:** `UserService.updateUser(UUID userId, UpdateUserRequest req, UserPrincipal actor, String ipAddress)`

Uses `findByIdWithFilter` for the same cross-tenant protection as `getUser`. If the user ID belongs to another tenant, 404 is returned.

**Partial update:** All fields in `UpdateUserRequest` are optional. Null fields are ignored — the existing value is preserved. This means you can update just the role, or just activate/deactivate, without sending the full user object.

```java
if (req.firstName() != null) user.setFirstName(req.firstName());
if (req.lastName()  != null) user.setLastName(req.lastName());
if (req.role()      != null) user.setRole(req.role());
if (req.active()    != null) user.setActive(req.active());
```

**Deactivation:** Setting `active: false` prevents the user from logging in. The `AuthService.login()` checks `user.active` after password verification and throws `ApiException(401, "Account is inactive")` if false. No data is deleted.

**Audit:** `AuditService.recordResource(UPDATE_USER, actor, "User", user.id, {role: ...}, ipAddress)`

---

### Change password — `PUT /api/v1/users/{id}/password`

**Role required:** Any authenticated user (`isAuthenticated()`)

**But only for their own account.** The service enforces this with an explicit check:

```java
if (!userId.equals(caller.getUserId())) {
    throw new ApiException(HttpStatus.FORBIDDEN, "Cannot change another user's password");
}
```

This means a LAB_ADMIN cannot change another user's password via this endpoint — they can deactivate users via `PUT /api/v1/users/{id}`, but password changes are self-service only.

**Service:** `UserService.changePassword(UUID userId, ChangePasswordRequest req, UserPrincipal caller, String ipAddress)`

**What happens:**

1. Verify `userId == caller.getUserId()` — throws `403` if not
2. Load user via `findByIdWithFilter` — 404 if not found (also protects cross-tenant)
3. Verify current password: `passwordEncoder.matches(req.currentPassword(), user.passwordHash)` — throws `401` if wrong
4. Hash new password: `passwordEncoder.encode(req.newPassword())` (BCrypt, strength 12)
5. Save user
6. Audit: `AuditService.recordAuth(PASSWORD_CHANGE, user.id, user.email, user.tenantId, ipAddress)`

**Returns:** `204 No Content`

---

## `User` Entity

**File:** `users/model/User.java`

| Field | Column | Type | Notes |
|---|---|---|---|
| `id` | `id` | UUID | PK, generated by `UUID.randomUUID()` in `@PrePersist` |
| `tenantId` | `tenant_id` | UUID (nullable) | Null for SUPER_ADMIN; not null for all lab users |
| `email` | `email` | VARCHAR(255) | Unique within tenant (partial unique index in DB) |
| `passwordHash` | `password_hash` | VARCHAR(255) | BCrypt hash, strength 12. Raw password never stored. |
| `firstName` | `first_name` | VARCHAR(100) | Display name |
| `lastName` | `last_name` | VARCHAR(100) | Display name |
| `role` | `role` | VARCHAR(50) | Stored as the enum name (e.g., `"LAB_ADMIN"`) |
| `active` | `active` | boolean | `false` = account disabled; login returns 401 |
| `createdAt` | `created_at` | TIMESTAMPTZ | Set in `@PrePersist`, never updated |
| `updatedAt` | `updated_at` | TIMESTAMPTZ | Set in `@PrePersist`, updated in `@PreUpdate` |

**Why `User` does NOT extend `TenantAwareEntity`:**

`TenantAwareEntity` declares `tenant_id` as `NOT NULL`. But SUPER_ADMIN has no tenant — `tenant_id` is `NULL` for SUPER_ADMIN rows. If `User` extended `TenantAwareEntity`, the database constraint would prevent saving SUPER_ADMIN.

Instead, `User` declares `@Filter` directly:

```java
@Entity
@Table(name = "users")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class User { ... }
```

This still applies the tenant filter for all lab user queries. When the filter is enabled (lab user token), the condition `AND tenant_id = '<tenantId>'` is appended. SUPER_ADMIN rows (where `tenant_id IS NULL`) are naturally excluded — the filter fires only when `TenantContextHolder.get()` returns a non-null UUID.

**`toPrincipal()` static factory:**

```java
public static UserPrincipal toPrincipal(User user) {
    return new UserPrincipal(
            user.id, user.tenantId, user.email,
            user.passwordHash, user.role.name(), user.active
    );
}
```

This converts a `User` JPA entity into a `UserPrincipal` (Spring Security's `UserDetails` implementation). It's called in `AuthService` after loading the user from the database during login and token refresh.

---

## DTOs

### `CreateUserRequest`

**File:** `users/dto/CreateUserRequest.java`

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `email` | String | `@NotBlank @Email` | Must be valid email format |
| `password` | String | `@NotBlank @Size(min=8, max=100)` | Raw password; minimum 8 characters |
| `firstName` | String | `@NotBlank @Size(max=100)` | |
| `lastName` | String | `@NotBlank @Size(max=100)` | |
| `role` | Role | `@NotNull` | One of the 5 roles; SUPER_ADMIN rejected at service layer (422) |

### `UpdateUserRequest`

**File:** `users/dto/UpdateUserRequest.java`

All fields are optional (nullable). Send only the fields you want to change.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `firstName` | String | `@Size(max=100)` (nullable) | Null = no change |
| `lastName` | String | `@Size(max=100)` (nullable) | Null = no change |
| `role` | Role | (nullable) | Null = no change |
| `active` | Boolean | (nullable) | `false` = deactivate; null = no change |

### `ChangePasswordRequest`

**File:** `users/dto/ChangePasswordRequest.java`

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `currentPassword` | String | `@NotBlank` | Must match the stored BCrypt hash |
| `newPassword` | String | `@NotBlank @Size(min=8)` | Minimum 8 characters |

### `UserResponse`

**File:** `users/dto/UserResponse.java`

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `tenantId` | UUID | Null for SUPER_ADMIN |
| `email` | String | |
| `firstName` | String | |
| `lastName` | String | |
| `role` | String | e.g., `"LAB_ADMIN"` |
| `active` | boolean | |
| `createdAt` | Instant | ISO-8601 UTC in JSON |

---

## Audit Events Emitted

| Action | When | Metadata |
|---|---|---|
| `CREATE_USER` | User created | `{"email": "user@lab.com", "role": "LAB_ANALYST"}` |
| `UPDATE_USER` | User updated | `{"role": "LAB_ADMIN"}` |
| `PASSWORD_CHANGE` | Password changed | None (no PHI, no token) |

Note: `DEACTIVATE_USER` is defined in `AuditAction` for future explicit use, but the current implementation emits `UPDATE_USER` when `active` is set to `false` via the update endpoint.

---

## Key Classes Summary

| Class | File | Responsibility |
|---|---|---|
| `UserController` | `users/controller/UserController.java` | HTTP endpoints; role-based `@PreAuthorize`; extracts client IP |
| `UserService` | `users/service/UserService.java` | Business logic: tenant enforcement, SUPER_ADMIN guard, password hashing |
| `User` | `users/model/User.java` | JPA entity; declares `@Filter` directly (nullable `tenant_id`); `toPrincipal()` factory |
| `Role` | `users/model/Role.java` | Enum of all 5 roles |
| `UserRepository` | `users/repository/UserRepository.java` | `findByIdWithFilter`, `findByEmailAndTenantId`, `existsByEmailAndTenantId` |
| `CreateUserRequest` | `users/dto/CreateUserRequest.java` | Validated request body for user creation |
| `UpdateUserRequest` | `users/dto/UpdateUserRequest.java` | Partial update request (all fields nullable) |
| `ChangePasswordRequest` | `users/dto/ChangePasswordRequest.java` | Current + new password |
| `UserResponse` | `users/dto/UserResponse.java` | Response body for all user endpoints |
