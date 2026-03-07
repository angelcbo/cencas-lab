# Cenicast LIS — Developer Documentation

Welcome to the Cenicast LIS codebase. This documentation is written for **any developer joining the team**, regardless of experience level. Every concept is explained from first principles, with links to external resources for deeper reading.

---

## What Is Cenicast LIS?

Cenicast LIS is a **multi-tenant Laboratory Information System (LIS)** SaaS platform for clinical laboratories in Mexico. Each lab that subscribes is called a **tenant** and gets its own fully isolated workspace. The platform is managed by a SUPER_ADMIN (the product owner), who creates and configures tenant accounts. From there, each lab's administrator manages their own staff, patients, test orders, and billing — all within their private tenant boundary.

---

## Documentation Map

| Document | What it covers |
|---|---|
| [Getting Started](./getting-started.md) | Local setup, Docker, dev credentials, first login walkthrough |
| [Architecture](./architecture.md) | Tech stack, package structure, request lifecycle diagram |
| [Security & Authentication](./security-and-auth.md) | JWT tokens, refresh tokens, cookies, BCrypt, Spring Security filter chain, RBAC |
| [Multi-Tenancy](./multitenancy.md) | How row-level tenant isolation works end-to-end (Hibernate @Filter, TenantContextHolder, AOP) |
| [Database](./database.md) | PostgreSQL schema, Liquibase migrations, data-type conventions |
| [API Conventions](./api-conventions.md) | Base URL, auth headers, error format, pagination, full endpoint reference |
| [Testing](./testing.md) | Test structure, Testcontainers, how to run tests, writing new tests |
| [Module: Auth](./modules/auth.md) | Login, token refresh, logout — request-by-request breakdown |
| [Module: Tenant](./modules/tenant.md) | Tenant CRUD, onboarding a new lab |
| [Module: Users](./modules/users.md) | User management, RBAC roles, password change |
| [Module: Audit](./modules/audit.md) | Audit logging, PHI rules, CorrelationIdFilter, GlobalExceptionHandler |

---

## Quick Endpoint Reference

| Method | Path | Role required | Module doc |
|---|---|---|---|
| POST | `/api/v1/auth/login` | None | [Auth](./modules/auth.md) |
| POST | `/api/v1/auth/refresh` | None (cookie) | [Auth](./modules/auth.md) |
| POST | `/api/v1/auth/logout` | Authenticated | [Auth](./modules/auth.md) |
| GET | `/api/v1/tenants` | SUPER_ADMIN | [Tenant](./modules/tenant.md) |
| POST | `/api/v1/tenants` | SUPER_ADMIN | [Tenant](./modules/tenant.md) |
| GET | `/api/v1/tenants/{id}` | SUPER_ADMIN | [Tenant](./modules/tenant.md) |
| PUT | `/api/v1/tenants/{id}` | SUPER_ADMIN | [Tenant](./modules/tenant.md) |
| DELETE | `/api/v1/tenants/{id}` | SUPER_ADMIN | [Tenant](./modules/tenant.md) |
| POST | `/api/v1/tenants/{tenantId}/users` | SUPER_ADMIN | [Tenant](./modules/tenant.md) |
| GET | `/api/v1/users` | LAB_ADMIN | [Users](./modules/users.md) |
| POST | `/api/v1/users` | LAB_ADMIN | [Users](./modules/users.md) |
| GET | `/api/v1/users/{id}` | LAB_ADMIN, LAB_RECEPTIONIST | [Users](./modules/users.md) |
| PUT | `/api/v1/users/{id}` | LAB_ADMIN | [Users](./modules/users.md) |
| PUT | `/api/v1/users/{id}/password` | Self (authenticated) | [Users](./modules/users.md) |
| GET | `/actuator/health` | None | — |

---

## Current Sprint Status

| Sprint | Scope | Status |
|---|---|---|
| Sprint 0 | Project scaffold (Docker, config, DB migrations) | ✅ Done |
| Sprint 1 | Auth, tenant management, user management, audit logging | ✅ Done |
| Sprint 2 | Patient demographics | 🔜 Next |
| Sprint 3 | Catalog (studies, panels, reference ranges) | Pending |
| Sprint 4 | Orders & samples | Pending |
| Sprint 5 | Result entry & validation | Pending |
| Sprint 6 | Result PDF generation | Pending |
| Sprint 7 | Billing & invoicing | Pending |
| Sprint 8 | Frontend (React + MUI) | Pending |
| Sprint 9 | Frontend core screens + CI hardening | Pending |

---

## Where to start?

- **First day**: [Getting Started](./getting-started.md) → get the stack running locally
- **Understanding the codebase**: [Architecture](./architecture.md) → understand the overall structure
- **Working on auth**: [Security & Authentication](./security-and-auth.md) + [Module: Auth](./modules/auth.md)
- **Working on a new tenant-scoped entity**: [Multi-Tenancy](./multitenancy.md) — read the checklist before writing code
- **Writing tests**: [Testing](./testing.md)
