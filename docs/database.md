# Database

---

## Why PostgreSQL?

We use PostgreSQL 15 (not MySQL, not SQLite, not H2). The reasons:

- **JSONB** — a native binary JSON column type. We use it in `audit_events.metadata` to store structured but schema-less context. JSONB supports GIN indexes for fast JSON key/value lookups.
- **Native UUID type** — UUIDs are stored efficiently as 16-byte binary, not as VARCHAR(36) strings.
- **TIMESTAMPTZ** — timezone-aware timestamp. All our timestamps are stored in UTC; TIMESTAMPTZ enforces this.
- **Partial indexes** — we use `WHERE tenant_id IS NOT NULL` and `WHERE tenant_id IS NULL` partial indexes for the email uniqueness constraint on the `users` table. This elegantly handles the SUPER_ADMIN case.
- **RLS (Row-Level Security)** — not used yet, but PostgreSQL's native RLS is a potential future backstop if we want database-level tenant isolation on top of the application-level Hibernate filter.

---

## Liquibase — Database Migrations

### What is a migration tool?

A database migration tool tracks changes to your schema over time. Instead of running `ALTER TABLE` commands manually, you write a **changeset** file, commit it to git, and the tool applies it automatically when the application starts.

> Reference: [Liquibase documentation](https://docs.liquibase.com/concepts/introduction-to-liquibase.html)

### Our conventions (mandatory — do not deviate)

- **Format: XML only.** No YAML, no SQL files, no Groovy.
- **Naming: `NNN-description.xml`** — e.g., `001-tenants.xml`, `010-audit.xml`. No Flyway-style `V1__` naming.
- **Inclusion order = FK dependency order.** A table must exist before another table references it with a foreign key.
- **`ddl-auto: validate`** — Hibernate does NOT auto-create or modify schema. Liquibase is the single source of truth for schema. If your entity field doesn't match the schema, the application will refuse to start.

### Master changelog

`backend/src/main/resources/db/changelog/db.changelog-master.xml`

This file lists all changeset files in order. **Every new changeset file must be added here** in the correct position (after its FK dependencies).

### Active changesets (Sprint 1)

| File | Table | Depends on |
|---|---|---|
| `001-tenants.xml` | `tenants` | Nothing |
| `002-users.xml` | `users` | `tenants` (FK) |
| `003-auth.xml` | `refresh_tokens` | `users`, `tenants` (FKs) |
| `010-audit.xml` | `audit_events` | Nothing (intentional — no app FKs) |

> Why `010` instead of `004`? Audit is logically grouped with the auth/users Sprint 1 work, but numbered with a gap to leave room for domain entity tables (004–009) that will be added in later sprints.

### Planned changesets (commented out in master)

| File | Table | Sprint |
|---|---|---|
| `004-patients.xml` | `patients` | Sprint 2 |
| `005-catalog.xml` | `catalog_studies`, `catalog_panels`, `reference_ranges` | Sprint 3 |
| `006-orders.xml` | `orders`, `order_items` | Sprint 4 |
| `007-samples.xml` | `samples` | Sprint 4 |
| `008-results.xml` | `results` | Sprint 5 |
| `009-billing.xml` | `billing_invoices` | Sprint 7 |

---

## Data Type Conventions

These conventions apply to **every** table. Do not deviate.

### UUIDs

```sql
-- In Postgres
id UUID NOT NULL DEFAULT gen_random_uuid()
```

```java
// In Java — always generate in Java, not the DB
@Id private UUID id;

@PrePersist
protected void onCreate() {
    id = UUID.randomUUID();
}
```

**Why Java-generated UUIDs?**  UUIDs from Java are available immediately after object creation, before the DB insert. This means you can reference the ID in logs, audit events, and responses without a round-trip to the database.

### Timestamps

```sql
-- In Postgres — always TIMESTAMPTZ (timezone-aware)
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

```java
// In Java — always Instant (UTC)
private Instant createdAt;
private Instant updatedAt;
```

**Never use** `TIMESTAMP WITHOUT TIME ZONE` in Postgres or `LocalDateTime` in Java for stored timestamps. Everything must be UTC.

### Money

```sql
-- In Postgres
unit_price NUMERIC(12,2)   -- up to 9,999,999,999.99
```

```java
// In Java
private BigDecimal unitPrice;
```

**Never use** `double`, `float`, or integer cents for money. Floating-point arithmetic introduces rounding errors that compound across invoices. `BigDecimal` is exact.

Tax calculation example:
```java
BigDecimal tax = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
```

### Tax Rate

```sql
-- Tax rate is stored as a decimal fraction, not a percentage
tax_rate NUMERIC(5,4) NOT NULL DEFAULT 0.1600  -- 16% IVA
```

`0.1600` = 16%. The `NUMERIC(5,4)` type can store up to `9.9999` (i.e., up to 999.99% which is more than enough).

---

## Schema Reference

### `tenants`

The root table. One row = one lab customer.

| Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|
| `id` | UUID | No | — | PK, generated in Java |
| `slug` | VARCHAR(63) | No | — | URL-safe identifier (e.g., `demo-lab`). **Immutable after creation.** |
| `name` | VARCHAR(255) | No | — | Display name (e.g., `"Demo Lab"`) |
| `timezone` | VARCHAR(50) | No | `America/Mexico_City` | IANA timezone. Used for display only; all data is UTC. |
| `tax_rate` | NUMERIC(5,4) | No | `0.1600` | IVA rate. `0.1600` = 16%. |
| `active` | BOOLEAN | No | `true` | Set to `false` when deactivated (soft delete) |
| `created_at` | TIMESTAMPTZ | No | `NOW()` | |
| `updated_at` | TIMESTAMPTZ | No | `NOW()` | |

**Constraints:** `UNIQUE(slug)`, PK on `id`

---

### `users`

One row per user account. `tenant_id IS NULL` indicates a SUPER_ADMIN.

| Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|
| `id` | UUID | No | — | PK, generated in Java |
| `tenant_id` | UUID | **Yes** | — | FK → `tenants.id`. NULL = SUPER_ADMIN. |
| `email` | VARCHAR(255) | No | — | Unique per tenant (see indexes below) |
| `password_hash` | VARCHAR(255) | No | — | BCrypt(12) hash — **never the raw password** |
| `first_name` | VARCHAR(100) | No | — | |
| `last_name` | VARCHAR(100) | No | — | |
| `role` | VARCHAR(50) | No | — | One of the 5 allowed values (CHECK constraint) |
| `active` | BOOLEAN | No | `true` | Inactive users cannot log in |
| `created_at` | TIMESTAMPTZ | No | `NOW()` | |
| `updated_at` | TIMESTAMPTZ | No | `NOW()` | |

**Constraints:**
- `CHECK(role IN ('SUPER_ADMIN','LAB_ADMIN','LAB_RECEPTIONIST','LAB_ANALYST','LAB_DOCTOR'))`
- FK: `tenant_id → tenants(id)`

**Indexes:**
- `UNIQUE(tenant_id, email) WHERE tenant_id IS NOT NULL` — email unique within a tenant
- `UNIQUE(email) WHERE tenant_id IS NULL` — SUPER_ADMIN email globally unique
- Index on `tenant_id` — for fast tenant-scoped lookups

**Why partial unique indexes?** Standard `UNIQUE(tenant_id, email)` would treat multiple NULL tenant_ids as distinct (NULL ≠ NULL in SQL). Partial indexes with `WHERE` clauses handle this correctly.

---

### `refresh_tokens`

One row per issued refresh token. Rows are never deleted — they form an audit chain.

| Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|
| `id` | UUID | No | — | PK, generated in Java |
| `token_hash` | VARCHAR(64) | No | — | SHA-256 hex of the raw UUID token. The raw token is never stored. |
| `user_id` | UUID | No | — | FK → `users.id` ON DELETE CASCADE |
| `tenant_id` | UUID | Yes | — | Denormalized from user for faster queries. NULL for SUPER_ADMIN. |
| `expires_at` | TIMESTAMPTZ | No | — | 30 days from creation |
| `revoked` | BOOLEAN | No | `false` | `true` = token can no longer be used |
| `family_id` | UUID | No | — | All tokens from the same login share this ID. Used for reuse detection. |
| `replaced_by` | UUID | Yes | — | Self-referential FK → `refresh_tokens.id`. Forms the rotation chain. |
| `ip_address` | VARCHAR(45) | Yes | — | IP where the token was issued. IPv6 can be up to 45 chars. |
| `created_at` | TIMESTAMPTZ | No | `NOW()` | |

**Constraints:**
- `UNIQUE(token_hash)` — prevents duplicate tokens
- FK: `user_id → users(id) ON DELETE CASCADE`
- FK: `replaced_by → refresh_tokens(id)`

**Indexes:**
- Index on `user_id` — find all tokens for a user
- Index on `family_id` — family revocation (`UPDATE ... WHERE family_id = ?`)

---

### `audit_events`

Immutable event log. Rows are **never updated or deleted**.

| Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|
| `id` | UUID | No | — | PK, generated on `@PrePersist` |
| `tenant_id` | UUID | Yes | — | NULL for SUPER_ADMIN actions |
| `actor_id` | UUID | No | — | Who performed the action. **No FK** — intentional, survives user deletion. |
| `actor_email` | VARCHAR(255) | No | — | Email at time of action |
| `action` | VARCHAR(100) | No | — | Event type (e.g., `LOGIN`, `CREATE_PATIENT`) |
| `resource_type` | VARCHAR(100) | Yes | — | What was affected (e.g., `"User"`, `"Tenant"`) |
| `resource_id` | UUID | Yes | — | ID of the affected resource |
| `metadata` | JSONB | Yes | — | Structured context. **Never PHI** (see PHI Rule in [Audit module docs](./modules/audit.md)) |
| `ip_address` | VARCHAR(45) | Yes | — | Client IP |
| `correlation_id` | VARCHAR(36) | Yes | — | From the `X-Correlation-Id` header |
| `created_at` | TIMESTAMPTZ | No | `NOW()` | |

**Why no FK on `actor_id`?** If we added `FOREIGN KEY (actor_id) REFERENCES users(id)`, deleting a user would cascade-delete their audit history — defeating the purpose of the audit log. The trade-off is intentional: audit integrity over referential integrity.

**Indexes:**
- `(tenant_id, created_at DESC)` — paginated audit log per tenant
- `(actor_id, created_at DESC)` — all actions by a specific user
- `(resource_type, resource_id)` — history of a specific resource
- GIN index on `metadata` — fast JSONB key/value queries

---

## Conventions for Future Tables

When adding a new table in a future sprint, follow this checklist:

- [ ] Use `UUID` for the primary key, generated in Java with `UUID.randomUUID()` in `@PrePersist`
- [ ] Use `TIMESTAMPTZ NOT NULL DEFAULT NOW()` for all datetime columns
- [ ] If tenant-scoped: add `tenant_id UUID NOT NULL REFERENCES tenants(id)` with an index
- [ ] Use `NUMERIC(12,2)` for money; `NUMERIC(5,4)` for rates/percentages
- [ ] Add changeset to `db.changelog-master.xml` in the correct FK dependency order
- [ ] Update the `TenantAwareEntity` subclass (or declare `@Filter` directly on the entity)
- [ ] Add `findByIdWithFilter` JPQL query to the repository (see [Multi-Tenancy](./multitenancy.md))
- [ ] Write a Liquibase comment on each column explaining what it stores
