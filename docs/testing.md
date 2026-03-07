# Testing

---

## Philosophy

Every sprint delivers integration tests alongside the production code. The rules:

1. **No skipping tests** — if a test fails, fix it before merging
2. **Tenant leakage tests are mandatory** — every new entity with a `tenant_id` must have at least one test proving cross-tenant access returns 404
3. **Real PostgreSQL in tests** — no H2, no mocking the database. We use Testcontainers.
4. **Both happy path and error path** — test that things work AND that they fail correctly

---

## Test Structure

All tests live in `backend/src/test/java/com/cenicast/lis/`.

### 1. `LisApplicationTest` — Context Load Test

**File:** `src/test/java/com/cenicast/lis/LisApplicationTest.java`

This test starts the entire Spring application context against a real PostgreSQL container. It has one test method: `contextLoads()`. The test body is empty — if Spring can start and Liquibase can apply all changesets without errors, the test passes.

This catches:
- Misconfigured beans (circular dependencies, missing `@Bean`s)
- Entity/schema mismatches (`ddl-auto: validate` means Hibernate validates every field against the real DB)
- Liquibase changeset errors

**Run this test after:** Adding a new entity, changing a column type, adding a new Spring bean.

### 2. `JwtServiceTest` — Pure Unit Tests

**File:** `src/test/java/com/cenicast/lis/common/security/JwtServiceTest.java`

A pure unit test — no Spring context, no database, runs in milliseconds. It constructs `JwtService` directly in `@BeforeEach` with a test secret.

| Test method | What it verifies |
|---|---|
| `issueAndValidateRoundTrip()` | Generate a token for a LAB_ADMIN user, validate it, assert all claims (userId, tenantId, role, email) match the input |
| `issueAndValidateRoundTrip_superAdmin_noTenantId()` | SUPER_ADMIN token has null tenantId; `extractTenantId()` returns null |
| `expiredToken_throws401()` | A token issued with `-1` minute expiry is immediately expired; `validateAndExtract()` throws `ApiException` with status 401 |
| `tamperedSignature_throws401()` | Corrupting the last 4 characters of a valid token makes it invalid; `validateAndExtract()` throws `ApiException` with status 401 |

### 3. `AuthIntegrationTest` — Full Integration Suite

**File:** `src/test/java/com/cenicast/lis/auth/AuthIntegrationTest.java`

The most important test class. Starts a full Spring Boot application on a random port with a real PostgreSQL container. Makes real HTTP requests using `TestRestTemplate`.

| Test category | Test methods |
|---|---|
| **Login** | `login_badPassword_returns401`, `login_inactiveUser_returns401`, `login_validLabAdmin_returns200AndSetsCookie`, `login_superAdmin_returns200_noTenantId` |
| **Refresh tokens** | `refresh_validCookie_rotatesToken`, `refresh_revokedToken_revokesFamily_returns401`, `refresh_expiredToken_returns401`, `logout_clearsSession` |
| **Tenant isolation** | `tenantA_cannotSeeTenantsB_users`, `superAdmin_cannotCallUsersEndpoint_returns403`, `tenantA_cannotGetTenantB_userById_returns404` |
| **Audit logging** | `auditEvent_failedLogin_persistedInDb`, `auditEvent_login_persistedInDb` |

**Total: 18 tests** (1 + 4 + 13) — all must be green before merging.

---

## Testcontainers

Testcontainers is a Java library that spins up real Docker containers for tests.

> Reference: [Getting started with Testcontainers for Java](https://testcontainers.com/guides/getting-started-with-testcontainers-for-java/)

**Why not H2 (in-memory database)?**

H2 is a popular in-memory database for testing. We don't use it because:
- H2 doesn't support `JSONB` (we use it in `audit_events.metadata`)
- H2 handles `UUID` differently than PostgreSQL
- H2 doesn't support our partial unique indexes with `WHERE` clauses on `users`
- Tests would pass on H2 but fail on real PostgreSQL — defeating the purpose

**How Testcontainers works in our tests:**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthIntegrationTest {

    // Declares a PostgreSQL 15 container (Docker image: postgres:15-alpine)
    // This container starts BEFORE the Spring context loads
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    // Wires the container's dynamic port into Spring's config
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    // ...
}
```

The `@Container` annotation on a `static` field means the container is shared across all tests in the class — it starts once and stays up for the entire test run (faster than starting a new container per test).

**Important — `@TestInstance(PER_CLASS)` is NOT used.** With JUnit 5's `PER_CLASS` lifecycle, Spring's context loading fires before Testcontainers starts the container, causing `"Mapped port can only be obtained after the container is started"`. We use the default `PER_METHOD` lifecycle with a `@BeforeEach` + static flag pattern instead.

---

## Running Tests

No local Java or Maven installation needed. Run tests via Docker:

```bash
cd /path/to/cenicast-lis

docker run --rm \
  -v "$(pwd)/backend":/app \
  -v ~/.m2:/root/.m2 \
  -w /app \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  maven:3.9-eclipse-temurin-17 \
  mvn test
```

**Expected output:**
```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.cenicast.lis.LisApplicationTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- in com.cenicast.lis.auth.AuthIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in com.cenicast.lis.common.security.JwtServiceTest
[INFO] Tests run: 18, Failures: 0, Errors: 0
[INFO] BUILD SUCCESS
```

**What the flags do:**
- `-v "$(pwd)/backend":/app` — mounts the backend source code into the container
- `-v ~/.m2:/root/.m2` — mounts your Maven local repository cache (avoids re-downloading dependencies)
- `-e TESTCONTAINERS_RYUK_DISABLED=true` — disables Ryuk (Testcontainers' cleanup daemon) which doesn't work in Docker-in-Docker
- `-e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` — tells Testcontainers to connect to the Docker host
- `-v /var/run/docker.sock:/var/run/docker.sock` — gives the Maven container access to the Docker daemon so it can start PostgreSQL containers

**To run only a specific test class:**
```bash
# Only run AuthIntegrationTest
docker run --rm \
  -v "$(pwd)/backend":/app \
  -v ~/.m2:/root/.m2 \
  -w /app \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  maven:3.9-eclipse-temurin-17 \
  mvn test -Dtest=AuthIntegrationTest
```

---

## Test Isolation Strategy

Each integration test creates its own isolated data. The test setup for `AuthIntegrationTest`:

- Two isolated test tenants are created: **Tenant A** (`integration-test-lab-a`) and **Tenant B** (`integration-test-lab-b`)
- Each tenant has one LAB_ADMIN user
- A SUPER_ADMIN is created by `DataInitializer` on startup (uses the `dev` profile)

**Double-checked locking for shared setup:**

Since creating test data is slow (database inserts), we create it once and reuse it across all tests in the class. We use a static flag with double-checked locking to ensure the setup runs exactly once, even if tests run in parallel:

```java
private static UUID tenantAId;
private static UUID userBId;
private static volatile boolean setupComplete = false;

@BeforeEach
void ensureTestDataExists() {
    if (setupComplete) return;  // fast path — skip if already done
    synchronized (AuthIntegrationTest.class) {
        if (setupComplete) return;  // double-check inside lock
        // ... create tenants and users ...
        setupComplete = true;  // must be last
    }
}
```

The `volatile` keyword on `setupComplete` ensures visibility across threads — once a thread sets it to `true`, all other threads immediately see the updated value.

---

## Writing New Integration Tests

When adding a new module (e.g., `patients` in Sprint 2), follow this pattern:

### 1. Create isolated test data in `@BeforeEach`

Don't reuse existing data from other tests. Create your own patients, orders, etc. with unique, hard-coded slugs/emails that won't conflict with other tests.

### 2. Test the happy path

```java
@Test
void createPatient_validRequest_returns201() {
    String tokenA = loginAndGetAccessToken(LAB_A_EMAIL, LAB_A_PASSWORD, LAB_A_SLUG);
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(tokenA);

    Map<String, Object> body = Map.of(
        "firstName", "Juan",
        "lastName", "García",
        "dateOfBirth", "1985-03-15",
        "sex", "M"
    );

    ResponseEntity<Map> response = rest.exchange(
        "/api/v1/patients", HttpMethod.POST,
        new HttpEntity<>(body, headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).containsKey("id");
}
```

### 3. Test the tenant isolation — always

```java
@Test
void tenantA_cannotGetTenantB_patient_returns404() {
    // Create a patient in Tenant B
    String patientBId = createPatientInTenantB();

    // Try to access it with Tenant A's token
    String tokenA = loginAndGetAccessToken(LAB_A_EMAIL, LAB_A_PASSWORD, LAB_A_SLUG);
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(tokenA);

    ResponseEntity<Map> response = rest.exchange(
        "/api/v1/patients/" + patientBId, HttpMethod.GET,
        new HttpEntity<>(headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}
```

### 4. Test authorization boundaries

```java
@Test
void labAnalyst_cannotCreatePatient_returns403() {
    String analystToken = loginAndGetAccessToken(ANALYST_EMAIL, ANALYST_PASS, LAB_A_SLUG);
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(analystToken);

    ResponseEntity<Map> response = rest.exchange(
        "/api/v1/patients", HttpMethod.POST,
        new HttpEntity<>(/* body */, headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
}
```

### 5. Test validation errors

```java
@Test
void createPatient_missingRequiredField_returns400() {
    String tokenA = loginAndGetAccessToken(LAB_A_EMAIL, LAB_A_PASSWORD, LAB_A_SLUG);
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(tokenA);

    Map<String, Object> body = Map.of("firstName", "Juan"); // missing lastName, dateOfBirth, sex

    ResponseEntity<Map> response = rest.exchange(
        "/api/v1/patients", HttpMethod.POST,
        new HttpEntity<>(body, headers), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("message").toString()).contains("lastName");
}
```
