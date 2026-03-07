# Architecture

---

## What Is Cenicast LIS?

A **Laboratory Information System (LIS)** is software that manages the complete workflow of a clinical lab: patient registration, test ordering, sample tracking, result entry, and billing. Cenicast LIS is a **multi-tenant SaaS** version of this — a single deployment that serves multiple independent labs, each completely isolated from one another.

Think of it like an apartment building: one building (one deployed application), many separate apartments (tenants), each with its own lock. A resident in apartment 3A can't access apartment 7B. The building manager (SUPER_ADMIN) has a master key to manage the building itself, but they respect each unit's privacy.

---

## Technology Stack

| Technology | Version | Purpose | Why we chose it | Reference |
|---|---|---|---|---|
| Java | 17 (LTS) | Backend language | Long-term support, mature ecosystem, strong typing | [dev.java/learn](https://dev.java/learn/) |
| Spring Boot | 3.4.3 | Application framework | Auto-configuration, embedded Tomcat, production-ready defaults | [docs.spring.io/spring-boot](https://docs.spring.io/spring-boot/) |
| Spring Security | (Spring Boot managed) | Authentication & authorization | Industry-standard, deep Spring integration, declarative `@PreAuthorize` | [docs.spring.io/spring-security](https://docs.spring.io/spring-security/) |
| Spring Data JPA + Hibernate 6 | (Spring Boot managed) | ORM + tenant filter | Hibernate's `@Filter` enables row-level tenant isolation transparently | [docs.spring.io/spring-data/jpa](https://docs.spring.io/spring-data/jpa/) |
| PostgreSQL | 15 | Relational database | JSONB for audit metadata, native UUID type, TIMESTAMPTZ, partial indexes | [postgresql.org/docs/15](https://www.postgresql.org/docs/15/) |
| Liquibase | (Spring Boot managed) | Database migrations | Tracks schema changes, supports rollback, XML format | [docs.liquibase.com](https://docs.liquibase.com/) |
| JJWT | 0.12.6 | JWT generation & validation | Modern API (0.12.x), no deprecated methods, actively maintained | [github.com/jwtk/jjwt](https://github.com/jwtk/jjwt) |
| Maven | 3.9 | Build tool | Standard Java build tool, dependency management, multi-stage Docker builds | [maven.apache.org](https://maven.apache.org/) |
| Docker + Docker Compose | 24+ / v2 | Container orchestration | Reproducible environments, same setup for dev and prod | [docs.docker.com/compose](https://docs.docker.com/compose/) |
| Testcontainers | 1.20.4 | Integration testing | Spins up real PostgreSQL in tests — no mocking, no H2 | [testcontainers.com](https://testcontainers.com/) |
| springdoc-openapi | 2.7.0 | Swagger UI | Auto-generates API docs from code annotations | [springdoc.org](https://springdoc.org/) |
| OpenPDF | 1.3.30 | PDF generation | For result PDFs (Sprint 6); Apache-licensed | [github.com/LibrePDF/OpenPDF](https://github.com/LibrePDF/OpenPDF) |
| logstash-logback-encoder | 8.0 | Structured JSON logging | Produces machine-readable logs for log aggregators | — |

---

## Modular Monolith

### What is a modular monolith?

A **monolith** means all the code runs in a single process — one JVM, one database connection pool, one deployment unit. A **modular** monolith means the code is organized into clearly-separated modules (packages) with defined boundaries, even though they share a runtime.

**Contrast with microservices:** Microservices split the application into multiple independent services, each with its own process and database. This adds operational complexity (service discovery, network calls, distributed tracing) that is overkill for an MVP serving 1–3 tenants.

**Why we chose modular monolith:**
- One deployment = simpler CI/CD, cheaper infrastructure at MVP scale
- Modules can be extracted into microservices later if needed (package boundaries are already clean)
- Shared database = no distributed transactions needed for things like billing + orders

---

## Package Structure

```
com.cenicast.lis
│
├── LisApplication.java                   ← Entry point (@SpringBootApplication)
│
├── config/                               ← Spring configuration beans
│   ├── SecurityConfig.java               ← HTTP security chain, BCrypt, filter registration
│   ├── WebMvcConfig.java                 ← CORS settings
│   ├── OpenApiConfig.java                ← Swagger UI configuration
│   └── JpaConfig.java                    ← Placeholder for future JPA customizations
│
├── common/                               ← Cross-cutting utilities (not domain-specific)
│   ├── audit/                            ← Immutable audit event log
│   │   ├── AuditEvent.java               ← @Entity: the audit_events table
│   │   ├── AuditEventRepository.java     ← Spring Data repository
│   │   ├── AuditAction.java              ← Enum of all auditable actions
│   │   └── AuditService.java             ← Records events (REQUIRES_NEW propagation)
│   ├── exception/                        ← Error handling
│   │   ├── ApiException.java             ← Custom RuntimeException with HttpStatus
│   │   ├── ErrorResponse.java            ← Standard JSON error response record
│   │   └── GlobalExceptionHandler.java   ← @RestControllerAdvice maps exceptions to HTTP
│   ├── init/
│   │   └── DataInitializer.java          ← Dev-only bootstrap (SUPER_ADMIN + demo tenant)
│   ├── persistence/                      ← JPA base classes and filter declaration
│   │   ├── package-info.java             ← @FilterDef for Hibernate tenant filter
│   │   └── TenantAwareEntity.java        ← @MappedSuperclass for tenant-scoped entities
│   ├── security/                         ← Auth/JWT/tenant infrastructure
│   │   ├── JwtService.java               ← Issues and validates JWT tokens
│   │   ├── JwtAuthFilter.java            ← Per-request JWT extraction filter
│   │   ├── TenantContextHolder.java      ← ThreadLocal<UUID> — current request's tenant
│   │   ├── TenantFilterAspect.java       ← AOP: enables Hibernate filter before repo calls
│   │   └── UserPrincipal.java            ← Stateless UserDetails built from JWT claims
│   └── util/
│       └── CorrelationIdFilter.java      ← Assigns X-Correlation-Id to every request
│
├── auth/                                 ← Authentication domain module
│   ├── controller/AuthController.java    ← POST /api/v1/auth/login|refresh|logout
│   ├── service/AuthService.java          ← Login, refresh, logout business logic
│   ├── repository/RefreshTokenRepository.java
│   ├── model/RefreshToken.java           ← @Entity: the refresh_tokens table
│   └── dto/                              ← LoginRequest, LoginResponse, etc.
│
├── tenant/                               ← Tenant management (SUPER_ADMIN only)
│   ├── controller/TenantController.java  ← GET/POST/PUT/DELETE /api/v1/tenants
│   ├── service/TenantService.java
│   ├── repository/TenantRepository.java
│   ├── model/Tenant.java                 ← @Entity: the tenants table
│   └── dto/                              ← TenantRequest, TenantResponse
│
├── users/                                ← User management
│   ├── controller/UserController.java    ← /api/v1/users CRUD + password change
│   ├── service/UserService.java
│   ├── repository/UserRepository.java
│   ├── model/
│   │   ├── User.java                     ← @Entity: the users table (nullable tenantId)
│   │   └── Role.java                     ← Enum: SUPER_ADMIN, LAB_ADMIN, etc.
│   └── dto/                              ← CreateUserRequest, UserResponse, etc.
│
│   ── Modules planned for future sprints ──
├── catalog/   (Sprint 3)    studies, panels, reference ranges
├── patients/  (Sprint 2)    patient demographics
├── orders/    (Sprint 4)    work orders, folio generation
├── samples/   (Sprint 4)    physical sample tracking
├── results/   (Sprint 5)    result entry + PDF generation
└── billing/   (Sprint 7)    invoicing, payments
```

---

## Request Lifecycle

Every HTTP request to the backend goes through this pipeline. Understanding this sequence is essential for debugging and for building new features correctly.

```
Incoming HTTP Request
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  CorrelationIdFilter  (common/util/CorrelationIdFilter.java) │
│                                                              │
│  1. Read X-Correlation-Id header (or generate a new UUID)   │
│  2. Put correlationId into MDC (log context)                 │
│  3. Set X-Correlation-Id on the response                     │
│  4. Always: MDC.remove("correlationId") in finally block     │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  JwtAuthFilter  (common/security/JwtAuthFilter.java)         │
│                                                              │
│  Skip for: /api/v1/auth/login, /api/v1/auth/refresh,        │
│            /actuator/*, /api/v1/docs*, /api/v1/api-docs*     │
│                                                              │
│  1. Read Authorization: Bearer <token> header                │
│  2. jwtService.validateAndExtract(token) → Claims            │
│  3. Build UserPrincipal from claims (no DB lookup!)          │
│  4. TenantContextHolder.set(principal.getTenantId())         │
│  5. Set authentication in SecurityContextHolder              │
│  6. chain.doFilter() — proceed to next filter                │
│  7. Always: TenantContextHolder.clear() in finally block     │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  Spring Security Authorization  (SecurityConfig.java)        │
│                                                              │
│  PUBLIC_PATHS → permitAll()                                  │
│  Everything else → must be authenticated                     │
│  (If not authenticated: 401 Unauthorized)                    │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  @PreAuthorize  (on each controller method)                  │
│                                                              │
│  e.g. @PreAuthorize("hasRole('LAB_ADMIN')")                  │
│  Checks principal's authorities against the expression       │
│  (If denied: 403 Forbidden)                                  │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  Controller  (e.g. UserController.java)                      │
│                                                              │
│  1. @Valid on @RequestBody → triggers Bean Validation        │
│  2. Extract path variables, query params                     │
│  3. Call service method                                      │
│  4. Return ResponseEntity                                    │
│  (If validation fails: 400 Bad Request with field errors)    │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  Service  (e.g. UserService.java)                            │
│                                                              │
│  @Transactional — opens a Hibernate Session                  │
│  Business logic, calls repository methods                    │
│  Throws ApiException on business rule violations             │
└─────────────────────────────────────────────────────────────┘
         │   (repository method call)
         ▼
┌─────────────────────────────────────────────────────────────┐
│  TenantFilterAspect  (common/security/TenantFilterAspect.java│
│                                                              │
│  @Before("execution(* com.cenicast.lis..*Repository.*(..))")  │
│                                                              │
│  Fires before EVERY repository method call.                  │
│  Gets tenantId from TenantContextHolder.get()                │
│  If tenantId != null:                                        │
│    session.enableFilter("tenantFilter")                      │
│      .setParameter("tenantId", tenantId)                     │
│  If tenantId == null (SUPER_ADMIN): does nothing             │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  Repository / Hibernate  (e.g. UserRepository.java)          │
│                                                              │
│  JPQL query executes.                                        │
│  Hibernate automatically appends:                            │
│    WHERE tenant_id = :tenantId                               │
│  to every query on @Filter-annotated entities.               │
│  Result: lab users ONLY see their own tenant's data.         │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
    PostgreSQL DB
         │
         ▼ (response travels back up)
┌─────────────────────────────────────────────────────────────┐
│  GlobalExceptionHandler  (common/exception/)                 │
│                                                              │
│  Catches exceptions and maps them to HTTP responses:         │
│  ApiException → status from exception                        │
│  Validation error → 400                                      │
│  AccessDeniedException → 403                                 │
│  AuthenticationException → 401                               │
│  Any other Exception → 500 (logged)                          │
└─────────────────────────────────────────────────────────────┘
```

---

## What Is Not Yet Implemented

The following modules are planned but not yet implemented. Their Liquibase changesets are in the master file but commented out:

| Module | Sprint | What it adds |
|---|---|---|
| Patients | Sprint 2 | Patient demographics: name, DOB, CURP, sex, phone, email |
| Catalog | Sprint 3 | Lab tests (studies), panels, reference ranges |
| Orders | Sprint 4 | Work orders linking patients to tests; folio number generation |
| Samples | Sprint 4 | Physical sample barcodes, collection and receipt tracking |
| Results | Sprint 5 | Numeric/text result entry, auto-flagging (H/L/N/A) |
| PDF | Sprint 6 | PDF result reports using OpenPDF |
| Billing | Sprint 7 | Invoices, IVA tax calculation, payment recording |
| Frontend | Sprint 8 | React + TypeScript + MUI SPA |
