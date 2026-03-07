# Cenicast LIS

Multi-tenant Laboratory Information System (LIS) for clinical labs in Mexico.

## Architecture

- **Backend**: Java 17, Spring Boot 3.x, Spring Security (JWT), Spring Data JPA, Liquibase
- **Frontend**: React + TypeScript + Vite (SPA), MUI v5
- **Database**: PostgreSQL 15
- **Multi-tenancy**: Row-level `tenant_id` (UUID) on all tenant-scoped tables
- **Auth**: JWT access tokens (15 min) + opaque refresh tokens (30 days, httpOnly cookie)

## Local Development

### Prerequisites

- Docker 24+
- Docker Compose v2

### Start the full stack

```bash
docker-compose up --build
```

Services:
- Backend API: http://localhost:8080
- Frontend:    http://localhost:3000
- PostgreSQL:  localhost:5432

### Verify

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}

# OpenAPI UI
open http://localhost:8080/api/v1/docs
```

## Default Dev Credentials

Bootstrapped automatically on first startup (`DataInitializer`, only active when `SPRING_PROFILES_ACTIVE != prod`).

| Account         | Email                    | Password       | Role          | Tenant     |
|-----------------|--------------------------|----------------|---------------|------------|
| Platform admin  | `admin@cenicast.com`     | `ChangeMe123!` | `SUPER_ADMIN` | —          |
| Demo lab admin  | `labadmin@demo.com`      | `ChangeMe123!` | `LAB_ADMIN`   | `demo-lab` |

Override by setting `app.init.*` in `application-dev.yml` or via environment variables:

```bash
APP_INIT_SUPER_ADMIN_EMAIL=myAdmin@example.com
APP_INIT_SUPER_ADMIN_PASSWORD=SecurePass123!
APP_INIT_LAB_ADMIN_EMAIL=admin@mylab.com
APP_INIT_LAB_ADMIN_PASSWORD=SecurePass123!
APP_INIT_DEMO_TENANT_SLUG=my-lab
APP_INIT_DEMO_TENANT_NAME="My Lab"
```

## Environment Variables

| Variable               | Default (dev)                                      | Description                          |
|------------------------|----------------------------------------------------|--------------------------------------|
| `DB_URL`               | `jdbc:postgresql://localhost:5432/cenicast_lis`    | JDBC connection URL                  |
| `DB_USER`              | `cenicast`                                         | Database username                    |
| `DB_PASSWORD`          | `cenicast`                                         | Database password                    |
| `JWT_SECRET`           | (dev default in application-dev.yml)               | HS256 signing secret (min 32 chars)  |
| `APP_DOMAIN`           | `app.cenicast.com`                                 | Cookie domain (prod only)            |
| `SPRING_PROFILES_ACTIVE` | `dev`                                            | Spring profile (`dev` / `prod`)      |

## Production Deploy

```bash
export DB_NAME=cenicast_lis
export DB_USER=...
export DB_PASSWORD=...
export JWT_SECRET=...        # at least 32 random characters
export APP_DOMAIN=app.cenicast.com

docker-compose -f docker-compose.prod.yml up --build -d
```

## Tenant Onboarding Runbook

1. Authenticate as `SUPER_ADMIN` via `POST /api/v1/auth/login`
2. Create a new tenant: `POST /api/v1/tenants`
3. Create the first `LAB_ADMIN` user: `POST /api/v1/users`
4. LAB_ADMIN logs in and configures the catalog: `POST /api/v1/catalog/studies`
5. Verify tenant isolation: all API calls from Tenant A must return zero rows from Tenant B

## Sprint Plan

| Sprint | Scope                              | Status      |
|--------|------------------------------------|-------------|
| 0      | Project scaffold                   | Complete    |
| 1      | Tenant + Auth + Users              | Pending     |
| 2      | Patients                           | Pending     |
| 3      | Catalog (studies, panels, ranges)  | Pending     |
| 4      | Orders + Samples                   | Pending     |
| 5      | Results entry + validation         | Pending     |
| 6      | Result PDF generation              | Pending     |
| 7      | Billing (invoices, payments)       | Pending     |
| 8      | Frontend scaffold + auth flow      | Pending     |
| 9      | Frontend core screens + hardening  | Pending     |

## Module Overview

| Module    | Responsibility                                             |
|-----------|------------------------------------------------------------|
| tenant    | Tenant CRUD (SUPER_ADMIN only)                             |
| auth      | Login, JWT issuance, refresh rotation, logout              |
| users     | User accounts per tenant, role assignment                  |
| catalog   | Studies, panels, reference ranges (tenant + global)        |
| patients  | Patient demographics, search                               |
| orders    | Work orders, folio generation, status lifecycle            |
| samples   | Physical sample tracking, barcode, status transitions      |
| results   | Result entry, auto-flagging vs reference ranges, PDF       |
| billing   | Invoices, payments, IVA tax                                |
| audit     | Immutable cross-cutting event log (AOP + explicit)         |
