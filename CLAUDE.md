# Cenicast LIS — Foundational Reference for AI Sessions

This file is read automatically by Claude Code at session start. It is the canonical source of truth for all conventions, decisions, and collaboration rules. Every session MUST honor everything here. Do not contradict or deviate from these decisions without an explicit instruction from the user and an update to this file.

---

## Project Overview

Multi-tenant Laboratory Information System (LIS) SaaS for clinical labs in Mexico. Solo founder. MVP targets 1–3 pilot labs.

**Repo layout:**
```
/backend/     — Spring Boot modular monolith
/frontend/    — React + Vite SPA (Sprint 8+; nginx placeholder until then)
docker-compose.yml
docker-compose.prod.yml
CLAUDE.md     ← this file
README.md
```

---

## Collaboration Rules

1. **Read before writing.** Always read existing files before modifying them. Never assume structure.
2. **Compile before moving on.** Every deliverable must compile (or at minimum have no syntax errors) before the session ends. Never leave the project in a broken state.
3. **No hidden placeholders.** If something is out of scope, say so explicitly. Do not add `// TODO: implement` stubs that mask missing logic.
4. **One step at a time.** Follow the sprint plan. Do not implement Sprint N+1 while delivering Sprint N.
5. **Tests are non-negotiable.** Every sprint includes integration tests via Testcontainers. Do not skip them.
6. **Security by default.** Every new endpoint must have `@PreAuthorize` or an explicit security annotation. No endpoint is accidentally public.
7. **Tenant leakage is a P0 bug.** Any code that could expose data across tenants must have a corresponding integration test.
8. **Update this file** when a significant architectural decision changes. Keep MEMORY.md in sync.

---

## Locked Technology Decisions

| Concern | Decision | Reason |
|---|---|---|
| Language / runtime | Java 17, Spring Boot 3.4.3 | LTS, ecosystem maturity |
| Base Maven package | `com.cenicast.lis` | Fixed — never change |
| Database | **PostgreSQL 15+** | JSONB for audit, native UUID, RLS backstop, recursive CTEs |
| Frontend | **React + TypeScript + Vite (SPA)** | No SSR needed for B2B auth-gated tool; simpler than Next.js |
| Multi-tenancy | **Row-level `tenant_id UUID NOT NULL`** | Simpler at MVP scale (1–3 tenants); Hibernate @Filter enforces it |
| Migrations | **Liquibase only** | YAML changelogs. Each entity in its own subfolder (`NNN-description/`). One `changelog.yml` per folder, one `.sql` per entity. No Flyway, no `V1__` naming. |
| Auth | JWT access token (15 min, HS256) + opaque refresh token (30 days, httpOnly cookie) | Standard stateless auth with rotation |
| Password hashing | BCrypt strength 12 | Spring Security default |
| UI library | **Tailwind CSS + shadcn/ui** | Overrides original MUI v5 decision |
| Server state | TanStack Query (React Query) | — |
| Forms | React Hook Form + Zod | — |
| PDF | `com.github.librepdf:openpdf` (Apache-licensed) | Implemented in Sprint 6, not deferred |

**DO NOT:**
- Switch to MySQL, H2 in production, or any other DB
- Use Next.js or any SSR framework
- Use Flyway or `V1__`-style migration naming
- Use schema-per-tenant multi-tenancy
- Store tokens in localStorage or sessionStorage
- Use integer cents for money

---

## Foundational Conventions

### Timestamps
- **Always UTC** — `TIMESTAMPTZ` in PostgreSQL, `Instant` in Java
- Never use `LocalDateTime` for stored timestamps

### Money
- **`NUMERIC(12,2)`** in PostgreSQL
- **`BigDecimal`** in Java — never `double`, never `float`, never integer cents
- Tax: `subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP)`

### IDs
- **UUID** for all public-facing entities
- Generated in Java: `UUID.randomUUID()` — never auto-increment, never DB-generated UUID for entities
- Stored as `uuid` native type in PostgreSQL

### API
- Base path: `/api/v1/`
- Error response shape (always):
  ```json
  {
    "timestamp": "<ISO-8601 UTC>",
    "status": 404,
    "error": "Not Found",
    "message": "Patient not found",
    "path": "/api/v1/patients/abc",
    "correlationId": "<UUID>"
  }
  ```
- Pagination: Spring `Pageable` (`?page=0&size=20&sort=createdAt,desc`)
- Correlation ID: `X-Correlation-Id` request/response header (set by `CorrelationIdFilter`)

### Code
- Everything in English: code, API paths, DB column names, comments
- UI copy can be Spanish — code never is
- No `@Transactional` on controllers — only on service methods
- `open-in-view: false` — always

---

## Architecture: Modular Monolith

### Package Structure

```
com.cenicast.lis
├── LisApplication.java
├── config/
│   ├── SecurityConfig.java       — filter chain, BCrypt bean, @EnableMethodSecurity
│   ├── OpenApiConfig.java        — Swagger UI at /api/v1/docs, Bearer JWT scheme
│   ├── JpaConfig.java            — enables Hibernate @Filter per request (Sprint 1)
│   └── WebMvcConfig.java         — CORS
├── common/
│   ├── audit/
│   │   ├── AuditEvent.java       — @Entity, NOT tenant-scoped
│   │   ├── AuditEventRepository.java
│   │   ├── AuditService.java
│   │   └── AuditAspect.java      — @Aspect, fires on @Auditable methods
│   ├── exception/
│   │   ├── ApiException.java
│   │   ├── GlobalExceptionHandler.java
│   │   └── ErrorResponse.java    — record with correlationId from MDC
│   ├── security/
│   │   ├── TenantContextHolder.java    — ThreadLocal<UUID>
│   │   ├── JwtService.java             — issue, validate, extract claims
│   │   ├── JwtAuthFilter.java          — OncePerRequestFilter
│   │   └── UserPrincipal.java          — implements UserDetails
│   ├── persistence/
│   │   ├── TenantAwareEntity.java      — @MappedSuperclass: id (UUID), tenant_id
│   │   └── AuditableEntity.java        — @MappedSuperclass: created_at/by, updated_at/by
│   └── util/
│       ├── CorrelationIdFilter.java    — MDC + X-Correlation-Id header
│       └── PaginationUtils.java
│
├── tenant/      controller/ service/ repository/ dto/ model/
├── auth/        controller/ service/ repository/ dto/ model/
├── users/       controller/ service/ repository/ dto/ model/
├── catalog/     controller/ service/ repository/ dto/ model/
├── patients/    controller/ service/ repository/ dto/ model/
├── orders/      controller/ service/ repository/ dto/ model/
├── samples/     controller/ service/ repository/ dto/ model/
├── results/     controller/ service/ repository/ dto/ model/
└── billing/     controller/ service/ repository/ dto/ model/
```

### Domain Module Responsibilities

| Module | Responsibility |
|---|---|
| `tenant` | Tenant CRUD (SUPER_ADMIN only). Name, slug, timezone, tax rate, active flag. |
| `auth` | Login, JWT issuance, refresh token rotation + revocation, logout. Audit: LOGIN, LOGOUT, TOKEN_REFRESH. |
| `users` | User accounts per tenant. Role assignment. Password change. BCrypt. |
| `catalog` | Studies (analytes), panels, reference ranges. Tenant catalog + global defaults (tenant_id IS NULL). |
| `patients` | Patient demographics. CURP, DOB, sex. Search by name/DOB. Tenant-scoped. |
| `orders` | Work orders: patient + studies/panels. Folio generation. Status lifecycle. |
| `samples` | Physical samples per order. Barcode, collection time, status transitions. |
| `results` | Result entry per order item. Auto-flag vs reference ranges (H/L/N/A). Validation workflow. PDF. |
| `billing` | Invoice from completed order. IVA tax. Payment recording. Folio generation. |
| `audit` | Immutable cross-cutting event log. AOP + explicit calls. NEVER logs PHI values. |

**NOT in MVP:** `inventory` (moved to MVP+)

---

## Security Design

### JWT Access Token Claims (HS256, 15 min TTL)
```json
{
  "sub":      "<userId UUID>",
  "tenantId": "<tenantId UUID>",
  "role":     "LAB_ADMIN",
  "email":    "user@lab.com",
  "iat":      1700000000,
  "exp":      1700000900,
  "jti":      "<unique token UUID>"
}
```
- `SUPER_ADMIN` has no `tenantId` claim (null)
- `jti` enables denylist if immediate revocation is needed

### Refresh Token Strategy
- **Not a JWT.** A `UUID` random value, stored **hashed (SHA-256)** in DB
- Delivered as **httpOnly cookie only** — never in JSON response body
- Frontend stores access token **in React memory only** (never localStorage/sessionStorage)

| Cookie attribute | Dev | Prod |
|---|---|---|
| `HttpOnly` | true | true |
| `Secure` | false | true |
| `SameSite` | `Lax` | `Strict` |
| `Path` | `/api/v1/auth` | `/api/v1/auth` |
| `Max-Age` | 30 days | 30 days |

**Rotation:** on `POST /api/v1/auth/refresh` → old token marked `revoked=true`, new token issued
**Reuse detection:** revoked token presented → revoke entire family for that user (security breach signal)

### RBAC Roles

| Role | Scope | Capabilities |
|---|---|---|
| `SUPER_ADMIN` | Platform | Manage tenants, create first LAB_ADMIN. No tenantId. |
| `LAB_ADMIN` | Tenant | All actions within tenant. |
| `LAB_RECEPTIONIST` | Tenant | Create patients, create orders, record payments. |
| `LAB_ANALYST` | Tenant | Enter and validate results. Read samples/orders. |
| `LAB_DOCTOR` | Tenant | Read-only: assigned patient results and PDFs. |
| `PATIENT` | Post-MVP | Read-only own results via sharing token. |

**Enforcement layers:**
1. `@PreAuthorize("hasRole('LAB_ADMIN')")` on controller methods
2. `TenantContextHolder.get()` checked in every service query
3. Hibernate `@Filter("tenantFilter")` at persistence layer

### Tenant Enforcement — End-to-End

```
HTTP Request
  └─ JwtAuthFilter
       ├─ Extract Bearer token
       ├─ jwtService.validateAndExtract() → claims
       ├─ TenantContextHolder.set(claims.tenantId)     ← ThreadLocal<UUID>
       ├─ Build UserPrincipal → SecurityContextHolder
       └─ chain.doFilter()

  └─ Controller: @PreAuthorize role check

  └─ Service: uses TenantContextHolder.get() in queries

  └─ Hibernate: @Filter("tenantFilter") condition tenant_id = :tenantId
       → every SQL query includes WHERE tenant_id = '<uuid>'

  └─ Response: JwtAuthFilter.finally → TenantContextHolder.clear()
```

**Tenant-scoped** (carry `tenant_id UUID NOT NULL`):
`users`, `patients`, `orders`, `order_items`, `samples`, `results`, `billing_invoices`, `catalog_studies` (when not global), `catalog_panels`, `reference_ranges`

**NOT tenant-scoped:** `tenants`, `audit_events`, `refresh_tokens`

**Mandatory CI integration tests (tenant leakage):**
1. Tenant A token + Tenant B patient ID → 404
2. Tenant A token + `GET /patients` → zero rows from Tenant B
3. Tenant A token + order with Tenant B patientId → 404/422
4. SUPER_ADMIN → cannot access `/patients` (no tenantId)
5. Revoked refresh token → 401 + family revocation

---

## Audit Strategy

### `audit_events` table (changeset `010-create-audit-events`)
```sql
CREATE TABLE audit_events (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID,                          -- NULL for SUPER_ADMIN actions
    actor_id       UUID         NOT NULL,
    actor_email    VARCHAR(255) NOT NULL,
    action         VARCHAR(100) NOT NULL,          -- LOGIN, CREATE_PATIENT, etc.
    resource_type  VARCHAR(100),
    resource_id    UUID,
    metadata       JSONB,                          -- context, NEVER PHI values
    ip_address     VARCHAR(45),
    correlation_id VARCHAR(36),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

### Capture mechanism

| Mechanism | Used for |
|---|---|
| **Explicit call** in service | Security events: LOGIN, FAILED_LOGIN, LOGOUT, TOKEN_REFRESH, TOKEN_REVOKED, PASSWORD_CHANGE |
| **`@Auditable` AOP aspect** | CRUD: CREATE/UPDATE_PATIENT, CREATE/UPDATE_ORDER, ENTER_RESULT, VALIDATE_RESULT, CREATE_INVOICE, RECORD_PAYMENT |

**PHI rule:** metadata stores IDs and status transitions only. NEVER store result numeric/text values in audit_events.

---

## Data Model — Key Tables

```
tenants           id, slug UNIQUE, name, timezone, tax_rate NUMERIC(5,4), active, created_at

users             id, tenant_id→tenants (NULL=SUPER_ADMIN), email, password_hash,
                  role, active, created_at, updated_at
                  UNIQUE(tenant_id, email)

refresh_tokens    id, token_hash UNIQUE, user_id→users, tenant_id,
                  expires_at, revoked BOOL, family_id, replaced_by,
                  ip_address, created_at

patients          id, tenant_id, first_name, last_name, date_of_birth DATE,
                  sex CHAR(1), curp VARCHAR(18), phone, email,
                  created_at, created_by, updated_at, updated_by
                  INDEX(tenant_id, last_name, first_name)

catalog_studies   id, tenant_id (NULL=global), code, name, sample_type,
                  turnaround_hours, price NUMERIC(12,2), active
                  UNIQUE(tenant_id, code)

catalog_panels    id, tenant_id, code, name, price NUMERIC(12,2), active

catalog_panel_studies   panel_id, study_id  [M:N join table]

reference_ranges  id, study_id, tenant_id, sex CHAR(1),
                  age_min_years INT, age_max_years INT,
                  low_value NUMERIC, high_value NUMERIC,
                  unit VARCHAR, text_range VARCHAR

orders            id, tenant_id, folio, patient_id→patients,
                  status [PENDING|IN_PROGRESS|COMPLETED|DELIVERED],
                  notes, created_at, created_by, updated_at, updated_by
                  UNIQUE(tenant_id, folio)

order_items       id, tenant_id, order_id→orders, study_id, panel_id,
                  unit_price NUMERIC(12,2), status

samples           id, tenant_id, order_id, barcode, sample_type,
                  collected_at, received_at,
                  status [COLLECTED|RECEIVED|PROCESSING|DONE]

results           id, tenant_id, order_item_id→order_items,
                  numeric_value NUMERIC, text_value TEXT,
                  result_type [NUMERIC|TEXT|QUALITATIVE],
                  unit, flag [H|L|N|A],
                  entered_by→users, entered_at,
                  validated_by→users, validated_at

billing_invoices  id, tenant_id, order_id→orders, folio,
                  status [DRAFT|ISSUED|PAID|CANCELLED],
                  subtotal NUMERIC(12,2), tax NUMERIC(12,2), total NUMERIC(12,2),
                  tax_rate NUMERIC(5,4), payment_method, paid_at,
                  created_at, updated_at
                  UNIQUE(tenant_id, folio)

audit_events      — see Audit Strategy section above
```

---

## Liquibase Changeset Plan

File format: **YAML only** (`changelog.yml`). SQL in a separate `.sql` file per entity. Each entity in its own subfolder (`NNN-description/`). No XML. No SQL inline in changelog files. No Flyway-style `V1__` naming.

Inclusion order in master changelog = execution order = dependency order. Never include a file before its FK targets exist.

```
src/main/resources/db/changelog/
├── db.changelog-master.yml
├── 001-tenants/               Sprint 1  — tenants table (no FKs to other tables)
│   ├── changelog.yml
│   └── 001-create-tenants.sql
├── 002-users/                 Sprint 1  — users table (FK → tenants)
│   ├── changelog.yml
│   └── 001-create-users.sql
├── 003-auth/                  Sprint 1  — refresh_tokens (FK → users, tenants)
│   ├── changelog.yml
│   └── 001-create-refresh-tokens.sql
├── 004-patients/              Sprint 2  — patients (FK → tenants)
├── 005-catalog/               Sprint 3  — studies, panels, reference_ranges
├── 006-orders/                Sprint 4  — orders, order_items (FK → patients)
├── 007-samples/               Sprint 4  — samples (FK → orders)
├── 008-results/               Sprint 5  — results (FK → order_items)
├── 009-billing/               Sprint 7  — billing_invoices (FK → orders)
└── 010-audit/                 Sprint 1  — audit_events (no app-level FKs)
    ├── changelog.yml
    └── 001-create-audit-events.sql
```

**Why 002 = users and 003 = auth:** `refresh_tokens` has a FK to `users`. Users must be created first.

---

## Sprint Plan

| Sprint | Scope | Key deliverables |
|---|---|---|
| **0** | Project scaffold | pom.xml, Dockerfile, configs, Liquibase master, CorrelationIdFilter, GlobalExceptionHandler, SecurityConfig stub — **DONE** |
| **1** | Tenant + Auth + Users | 001/002/003/010 changesets (in that order), JWT+refresh flow, TenantContextHolder, Hibernate @Filter wiring, RBAC |
| **2** | Patients | 004 changeset, patient CRUD + search, @Auditable |
| **3** | Catalog | 005 changeset, studies + panels + reference ranges, global vs tenant catalog |
| **4** | Orders + Samples | 006/007 changesets, folio gen, status FSM, sample barcode tracking |
| **5** | Results | 008 changeset, result entry, auto-flag vs reference ranges, validation workflow |
| **6** | PDF | ResultPdfService (OpenPDF), `GET /orders/{id}/result-pdf` → application/pdf |
| **7** | Billing | 009 changeset, invoice from order, IVA, payment recording |
| **8** | Frontend scaffold | Vite+React+MUI+TanStack Query, Axios 401 interceptor, AuthContext (memory-only token), protected routes |
| **9** | Frontend core + hardening | All screens, role-gated UI, Testcontainers CI suite, cross-tenant leakage tests, structured JSON logging |

---

## Environment Variables Reference

| Variable | Dev default | Required in prod |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/cenicast_lis` | Yes |
| `DB_USER` | `cenicast` | Yes |
| `DB_PASSWORD` | `cenicast` | Yes |
| `JWT_SECRET` | dev fallback in application.yml (≥32 chars) | Yes |
| `SPRING_PROFILES_ACTIVE` | `dev` | Yes (`prod`) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Yes |
| `APP_DOMAIN` | `app.cenicast.com` | Yes |
| `COOKIE_SECURE` | `false` | Yes (`true`) |
| `COOKIE_SAME_SITE` | `Lax` | Yes (`Strict`) |

---

## Post-MVP Roadmap (out of scope above)

| Priority | Item |
|---|---|
| MVP+ | Inventory module (reagent stock, low-stock alerts) |
| MVP+ | Multi-branch support |
| P1 | Patient result portal (sharing tokens) |
| P2 | ASTM/HL7 instrument integrations |
| P3 | CFDI (Mexico invoicing) |
| P4 | SSO (SAML/OIDC) |
