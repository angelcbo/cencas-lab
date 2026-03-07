# Multi-Tenancy

This document explains how Cenicast LIS ensures that data from one lab (tenant) is completely invisible to users of another lab. Read this document carefully before building any new feature that touches tenant-scoped data.

---

## What Is Multi-Tenancy?

**Multi-tenancy** means a single deployed application serves multiple independent customers (tenants), each with complete data isolation. No tenant can see, modify, or interfere with another tenant's data.

Think of it like a shared apartment building:
- The building (application) is shared infrastructure
- Each apartment (tenant) has its own locked door
- Residents (users) of apartment 3A cannot enter apartment 7B
- The building manager (SUPER_ADMIN) manages the building itself, but respects each unit's privacy

> Reference: [Cloudflare — What is multitenancy?](https://www.cloudflare.com/learning/cloud/what-is-multitenancy/)

---

## Our Approach: Row-Level Isolation

There are three common approaches to multi-tenancy:

| Approach | How it works | Pros | Cons |
|---|---|---|---|
| Separate databases | Each tenant gets their own database | Maximum isolation | Expensive to operate at scale; complex migrations |
| Separate schemas | One database, one schema per tenant | Good isolation | Schema migrations must run N times (once per tenant) |
| **Row-level isolation** ← our choice | One schema, `tenant_id` column on every table | Simple to operate; easy migrations | Must be careful not to miss the filter in queries |

We add a `tenant_id UUID` column to every tenant-scoped table. All queries automatically include `WHERE tenant_id = :currentTenantId` — enforced at the Hibernate level so no individual query can forget it.

---

## Which Tables Are Tenant-Scoped?

| Table | Tenant-scoped? | Why |
|---|---|---|
| `tenants` | **No** | This IS the tenant root — it makes no sense to filter it by itself |
| `users` | **Yes** (nullable) | Lab users belong to a tenant. SUPER_ADMIN has `tenant_id = NULL`. |
| `refresh_tokens` | **No** | Owned by the user, not filtered by tenant |
| `audit_events` | **No** | Cross-cutting; SUPER_ADMIN must be able to see all events |
| `patients` *(Sprint 2+)* | **Yes** | Strict isolation — Tenant A cannot see Tenant B's patients |
| `orders`, `order_items` *(Sprint 4+)* | **Yes** | Each lab's orders are private |
| `samples` *(Sprint 4+)* | **Yes** | Sample barcodes belong to a lab |
| `results` *(Sprint 5+)* | **Yes** | Lab results are sensitive and private |
| `billing_invoices` *(Sprint 7+)* | **Yes** | Financial data is strictly isolated |
| `catalog_studies`, `catalog_panels` *(Sprint 3+)* | **Yes** (with global override: `tenant_id IS NULL`) | Per-tenant catalog, with `NULL` meaning "global/default" |
| `reference_ranges` *(Sprint 3+)* | **Yes** | Per-tenant reference ranges |

---

## How the Tenant Filter Works — End to End

The isolation system involves six components working together. Here is the complete flow for every authenticated request from a lab user:

```
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 1: JWT carries tenantId                                          │
│                                                                       │
│ When a lab user logs in, AuthService issues a JWT containing:         │
│   { "sub": "<userId>", "tenantId": "<tenantAId>", "role": "LAB_ADMIN" }│
│                                                                       │
│ This JWT is signed — the tenantId cannot be forged.                   │
│ Class: JwtService.generateAccessToken()                               │
│ File:  common/security/JwtService.java                                │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 2: JwtAuthFilter extracts tenantId                               │
│                                                                       │
│ Every request with a Bearer token passes through JwtAuthFilter.       │
│ It calls jwtService.validateAndExtract(token) to get the claims,      │
│ then:                                                                 │
│                                                                       │
│   UUID tenantId = jwtService.extractTenantId(claims); // "tenantAId" │
│   TenantContextHolder.set(tenantId);                                  │
│                                                                       │
│ Class: JwtAuthFilter                                                  │
│ File:  common/security/JwtAuthFilter.java                             │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 3: TenantContextHolder stores tenantId for the request           │
│                                                                       │
│ TenantContextHolder is a simple wrapper around a ThreadLocal<UUID>.   │
│ A ThreadLocal stores a value that is:                                 │
│   - Specific to the current thread                                    │
│   - Invisible to other threads                                        │
│                                                                       │
│ In a web server, each HTTP request runs on its own thread.            │
│ So TenantContextHolder.get() always returns THIS request's tenant,    │
│ never another request's tenant.                                       │
│                                                                       │
│   TenantContextHolder.set(tenantId)    // called by JwtAuthFilter     │
│   TenantContextHolder.get()           // called by TenantFilterAspect │
│   TenantContextHolder.clear()         // called in finally block      │
│                                                                       │
│ IMPORTANT: Always call clear() not set(null). set(null) keeps the     │
│ ThreadLocal slot allocated; clear() / remove() releases it.           │
│                                                                       │
│ Class: TenantContextHolder                                            │
│ File:  common/security/TenantContextHolder.java                       │
│ Reference: https://docs.oracle.com/en/java/tutorial/essential/        │
│            concurrency/threadlocalvars.html                           │
└──────────────────────────────────────────────────────────────────────┘
                              │
                (request reaches service, @Transactional opens a Session)
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 4: TenantFilterAspect intercepts the repository call             │
│                                                                       │
│ Spring AOP (Aspect-Oriented Programming) lets us run code            │
│ automatically before/after certain method calls, without modifying    │
│ those methods. We use it to enable the Hibernate filter               │
│ before every repository method call.                                  │
│                                                                       │
│ The pointcut (what to intercept):                                     │
│   @Before("execution(* com.cenicast.lis..*Repository.*(..))")        │
│                                                                       │
│ Plain English: "Before any method call on any class named *Repository │
│ in any subpackage of com.cenicast.lis, run this code."               │
│                                                                       │
│ The advice (what to run):                                             │
│   UUID tenantId = TenantContextHolder.get();                          │
│   if (tenantId == null) return;  // SUPER_ADMIN — no filter           │
│   Session session = entityManager.unwrap(Session.class);             │
│   session.enableFilter("tenantFilter")                                │
│           .setParameter("tenantId", tenantId);                        │
│                                                                       │
│ This is safe because:                                                 │
│   - Services are @Transactional, so a Hibernate Session is            │
│     already open when the aspect fires                                │
│   - The EntityManager proxy is thread-bound — unwrap() gives          │
│     us the active Session for this exact request                      │
│   - enableFilter() on an already-enabled filter is idempotent        │
│     in Hibernate 6 (safe to call multiple times)                      │
│                                                                       │
│ Class: TenantFilterAspect                                             │
│ File:  common/security/TenantFilterAspect.java                        │
│ Reference: https://docs.spring.io/spring-framework/reference/         │
│            core/aop.html                                              │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 5: Hibernate @Filter appends WHERE clause to every query         │
│                                                                       │
│ Hibernate filters are a declarative mechanism to add extra            │
│ conditions to every query on a given entity class.                    │
│                                                                       │
│ The filter is DEFINED once in package-info.java:                      │
│   @FilterDef(name = "tenantFilter",                                   │
│              parameters = @ParamDef(name = "tenantId", type = UUID.class))│
│                                                                       │
│ The filter is APPLIED to each entity with @Filter:                    │
│   @Entity                                                             │
│   @Filter(name = "tenantFilter", condition = "tenant_id = :tenantId") │
│   public class User { ... }                                           │
│                                                                       │
│ When the filter is enabled on a Session, Hibernate appends            │
│   AND tenant_id = '<tenantAId>'                                       │
│ to every SQL query that touches that entity.                          │
│                                                                       │
│ Example query generated:                                              │
│   SELECT * FROM users WHERE id = ? AND tenant_id = '<tenantAId>'     │
│                                                                       │
│ Result: Tenant A's user looking up Tenant B's user UUID gets zero     │
│ rows → 404 Not Found.                                                 │
│                                                                       │
│ Classes: package-info.java, TenantAwareEntity, User                   │
│ Files:   common/persistence/package-info.java                         │
│          common/persistence/TenantAwareEntity.java                    │
│          users/model/User.java                                        │
│ Reference: https://docs.jboss.org/hibernate/orm/6.0/userguide/        │
│            html_single/Hibernate_User_Guide.html#pc-filter            │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 6: TenantAwareEntity — the base class                            │
│                                                                       │
│ Most tenant-scoped entities extend TenantAwareEntity:                 │
│                                                                       │
│   @MappedSuperclass                                                   │
│   @Filter(name = "tenantFilter", condition = "tenant_id = :tenantId") │
│   public abstract class TenantAwareEntity {                           │
│       @Id private UUID id;                                            │
│       @Column(name = "tenant_id", nullable = false) private UUID tenantId;│
│       private Instant createdAt;                                      │
│       private Instant updatedAt;                                      │
│       // @PrePersist, @PreUpdate, getters/setters...                  │
│   }                                                                   │
│                                                                       │
│ @MappedSuperclass means "share these fields and annotations with       │
│ subclasses but don't create a database table for TenantAwareEntity    │
│ itself."                                                              │
│                                                                       │
│ Subclasses inherit the @Filter automatically.                         │
│                                                                       │
│ Exception — User:                                                      │
│   User.tenant_id is nullable (SUPER_ADMIN has no tenant).             │
│   TenantAwareEntity declares tenant_id as NOT NULL.                   │
│   So User declares @Filter directly (not by extending the base class).│
│                                                                       │
│ Class: TenantAwareEntity                                              │
│ File:  common/persistence/TenantAwareEntity.java                      │
└──────────────────────────────────────────────────────────────────────┘
                              │
                    (response travels back up)
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 7: Cleanup — TenantContextHolder.clear()                         │
│                                                                       │
│ In JwtAuthFilter.doFilterInternal(), the filter cleanup runs in a     │
│ finally block — this means it runs even if an exception occurs:       │
│                                                                       │
│   try {                                                               │
│       chain.doFilter(request, response);                              │
│   } finally {                                                         │
│       TenantContextHolder.clear();   // always runs                   │
│   }                                                                   │
│                                                                       │
│ Why is this critical?                                                 │
│ Web server threads are reused across requests (thread pool).          │
│ If we forget to clear the ThreadLocal, the next request handled       │
│ by this thread would start with the previous request's tenantId.      │
│ That would be a data leak.                                            │
└──────────────────────────────────────────────────────────────────────┘
```

---

## SUPER_ADMIN and the Filter

SUPER_ADMIN is a special case:

1. The SUPER_ADMIN JWT has no `tenantId` claim (`null`)
2. `JwtAuthFilter` calls `TenantContextHolder.set(null)` — but wait, we actually call `set()` only when tenantId is non-null. When it's null, `TenantContextHolder.get()` returns `null`.
3. `TenantFilterAspect` sees `tenantId == null` and **returns early without enabling the filter**
4. Hibernate runs queries without the tenant filter — SUPER_ADMIN could potentially see all tenants' data

**But this is safe.** SUPER_ADMIN is blocked from tenant-scoped endpoints by `@PreAuthorize`:
- `GET /api/v1/users` requires `hasRole('LAB_ADMIN')`
- `GET /api/v1/patients` (Sprint 2+) will require `hasRole('LAB_ADMIN')` or `hasRole('LAB_RECEPTIONIST')`

SUPER_ADMIN only has the `SUPER_ADMIN` role — not `LAB_ADMIN`. Spring Security rejects the request at the controller method level before it even reaches the service or repository.

**Two layers of defense:**
1. **Primary guard**: `@PreAuthorize` — SUPER_ADMIN is forbidden from lab-user endpoints
2. **Defense-in-depth**: Hibernate filter — even if an endpoint is accidentally not secured, SUPER_ADMIN queries don't leak cross-tenant data to lab users (lab users always have a non-null tenantId)

---

## The `findById` Problem

### Why `findById` breaks isolation

Spring Data JPA's `findById(UUID id)` method uses `EntityManager.find()` under the hood. In Hibernate 6, `find()` is a **direct primary-key lookup** that checks the first-level cache (session cache) first and, on a cache miss, performs:

```sql
SELECT * FROM users WHERE id = ?
```

Notice: the `AND tenant_id = :tenantId` clause from our Hibernate filter is **NOT applied** to `find()`. The filter only applies to JPQL queries (those that go through Hibernate's query engine).

**This means:** if a Tenant A user calls `GET /api/v1/users/<tenant-B-user-id>`, and the service uses `findById`, the query returns Tenant B's user. **Data leak.**

### Our fix: `findByIdWithFilter`

We add a custom JPQL query to every tenant-scoped repository where ID lookups are needed:

```java
// UserRepository.java
@Query("SELECT u FROM User u WHERE u.id = :id")
Optional<User> findByIdWithFilter(@Param("id") UUID id);
```

JPQL goes through Hibernate's query engine → the filter is applied → the effective SQL becomes:
```sql
SELECT * FROM users WHERE id = ? AND tenant_id = '<tenantId>'
```

Tenant B's user UUID with Tenant A's token → no rows found → 404. Correct behaviour.

**Rule: Never use `findById` on tenant-scoped entities.** Always use a custom `@Query`.

---

## Writing New Tenant-Scoped Entities

Follow this checklist every time you add a new tenant-scoped entity in a future sprint:

- [ ] **Liquibase changeset**: Add `tenant_id UUID NOT NULL REFERENCES tenants(id)` column. Add an index on `tenant_id`. Place the changeset after its FK dependencies in `db.changelog-master.xml`.
- [ ] **Entity class**: Extend `TenantAwareEntity` if `tenant_id` is `NOT NULL`. If `tenant_id` is nullable (edge cases only), declare `@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")` directly on the entity.
- [ ] **Repository**: Add `findByIdWithFilter` with `@Query("SELECT e FROM Entity e WHERE e.id = :id")`. Never use the inherited `findById`.
- [ ] **Service**: Use `findByIdWithFilter` in all service methods. Set `entity.setTenantId(tenantId)` from `TenantContextHolder.get()` (or from `principal.getTenantId()`) on creation.
- [ ] **Integration test**: Write a test that proves cross-tenant ID lookup returns 404. Example:
  ```java
  // Tenant A's token + Tenant B's entity ID → must return 404
  ResponseEntity<Map> response = rest.exchange(
      "/api/v1/patients/" + patientBId, HttpMethod.GET,
      new HttpEntity<>(headersWithTokenA), Map.class);
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  ```
- [ ] **Controller**: Apply `@PreAuthorize("hasAnyRole('LAB_ADMIN', ...)")` — not just `isAuthenticated()`. SUPER_ADMIN must be explicitly blocked from tenant-scoped endpoints.
