# Getting Started

This guide gets you from zero to a running local environment in a few minutes. **You do not need Java or Maven installed** — everything runs inside Docker.

---

## Prerequisites

| Tool | Minimum version | Install |
|---|---|---|
| Docker | 24+ | https://docs.docker.com/get-docker/ |
| Docker Compose | v2 (bundled with Docker Desktop) | Included in Docker Desktop |
| `jq` (optional) | Any | Used in curl examples to pretty-print JSON. `brew install jq` on macOS. |

Verify your setup:

```bash
docker --version          # Docker version 24.x.x or higher
docker compose version    # Docker Compose version v2.x.x or higher
```

---

## Two Ways to Run Locally

### Option A — Full stack via Docker Compose (simplest)

Everything runs in Docker: database, backend, and frontend placeholder.

```bash
git clone <repo-url> cenicast-lis
cd cenicast-lis
docker compose up --build
```

The first run downloads images and compiles the backend — this takes 2–5 minutes. Subsequent starts are much faster.

| Service | URL | Notes |
|---|---|---|
| Backend (Spring Boot) | http://localhost:8080 | REST API |
| Frontend (nginx placeholder) | http://localhost:3000 | Static placeholder; React app is Sprint 8 |
| PostgreSQL | localhost:5432 | DB name: `cenicast_lis`, user: `cenicast`, password: `cenicast` |

You'll see the backend is ready when:
```
cenicast-backend | Started LisApplication in X.XXX seconds
```

---

### Option B — Database in Docker, backend on host (recommended for active backend development)

Run only the database in Docker and start the Spring Boot application directly on your machine. This gives you faster restarts, easy debugger attachment, and hot-reload via your IDE.

**Prerequisites for this option:** Java 17+ and Maven 3.9+ installed locally.

**Step 1 — Start the database:**

```bash
# From the repository root — run once; keep it running in the background
./backend/scripts/init_dependencies.sh
```

This starts only the PostgreSQL container and waits until it is healthy. Your global Docker and Spring Boot processes stay independent.

**Step 2 — Start the backend:**

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

The backend connects to `localhost:5432` (the container started in Step 1).

**Stop the database when done:**

```bash
docker compose stop db
```

---

## Verify the Backend Is Running

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

---

## Swagger UI (Interactive API Explorer)

Open **http://localhost:8080/api/v1/docs** in your browser.

This is an interactive API explorer. You can:
- Browse all available endpoints
- See request/response schemas with examples
- Make real API calls directly from the browser

To make authenticated calls in Swagger UI:
1. Call `POST /api/v1/auth/login` to get an `accessToken`
2. Click the **Authorize** button (lock icon, top right)
3. Enter: `Bearer <your-accessToken>` and click **Authorize**
4. All subsequent requests in Swagger UI will include the Bearer token

---

## Default Dev Credentials

The application auto-creates these accounts on startup (only in `dev` profile — never in production):

| Account | Email | Password | Role | Tenant slug |
|---|---|---|---|---|
| Platform admin | `admin@cenicast.com` | `ChangeMe123!` | `SUPER_ADMIN` | *(none — platform-level)* |
| Demo lab admin | `labadmin@demo.com` | `ChangeMe123!` | `LAB_ADMIN` | `demo-lab` |

These are created by `DataInitializer.java` (`common/init/DataInitializer.java`) which runs at startup and is idempotent (safe to run multiple times).

---

## First Login Walkthrough

### 1. Log in as SUPER_ADMIN

SUPER_ADMIN does not belong to any tenant, so `tenantSlug` is omitted:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@cenicast.com","password":"ChangeMe123!"}' \
  | jq -r '.accessToken')

echo $TOKEN
```

### 2. Log in as a LAB_ADMIN

Lab users must specify their tenant's slug:

```bash
curl -s -c /tmp/lis-cookies.txt \
  -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"labadmin@demo.com","password":"ChangeMe123!","tenantSlug":"demo-lab"}' \
  | jq .
```

The response includes an `accessToken`. A `refresh_token` cookie is also set (httpOnly — used by the browser/curl cookie jar automatically).

### 3. Use the access token in subsequent requests

```bash
LAB_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"labadmin@demo.com","password":"ChangeMe123!","tenantSlug":"demo-lab"}' \
  | jq -r '.accessToken')

# List users in your tenant
curl -s -H "Authorization: Bearer $LAB_TOKEN" \
  http://localhost:8080/api/v1/users | jq .
```

### 4. Refresh the access token

The access token expires in 15 minutes. Use the refresh cookie to get a new one:

```bash
curl -s -b /tmp/lis-cookies.txt -c /tmp/lis-cookies.txt \
  -X POST http://localhost:8080/api/v1/auth/refresh | jq .
```

### 5. Log out

```bash
curl -s -b /tmp/lis-cookies.txt \
  -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer $LAB_TOKEN"
# Returns 204 No Content; refresh cookie is cleared
```

---

## pgAdmin (Optional Database GUI)

Start pgAdmin alongside the stack:

```bash
docker compose --profile tools up
```

Access at **http://localhost:5050**

| Setting | Value |
|---|---|
| Login email | `admin@local.dev` |
| Login password | `admin` |
| DB host | `db` (Docker network name) |
| DB port | `5432` |
| DB name | `cenicast_lis` |
| DB user | `cenicast` |
| DB password | `cenicast` |

---

## Environment Variables Reference

All config values are in `backend/src/main/resources/application.yml`. Override them with environment variables in production.

| Variable | Dev default | Required in prod | Description |
|---|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/cenicast_lis` | Yes | Full JDBC connection URL |
| `DB_USER` | `cenicast` | Yes | Database username |
| `DB_PASSWORD` | `cenicast` | Yes | Database password |
| `JWT_SECRET` | dev fallback (in yml) | Yes | HS256 signing secret — must be ≥32 chars |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Yes | Frontend origin(s), comma-separated |
| `COOKIE_SECURE` | `false` | Yes (`true`) | Set `true` in prod to require HTTPS for cookies |
| `COOKIE_SAME_SITE` | `Lax` | Yes (`Strict`) | Cookie SameSite policy |
| `COOKIE_DOMAIN` | `localhost` | Yes | Cookie domain (e.g., `app.cenicast.com`) |
| `SPRING_PROFILES_ACTIVE` | `dev` | Yes (`prod`) | Spring profile — controls DataInitializer, logging |

---

## Production Deploy

```bash
# Set required environment variables
export DB_NAME=cenicast_lis
export DB_USER=<secure-user>
export DB_PASSWORD=<secure-password>
export JWT_SECRET=<random-64-char-secret>
export APP_DOMAIN=app.cenicast.com

# Start the production stack
docker compose -f docker-compose.prod.yml up --build -d
```

In production:
- `DataInitializer` does **not** run (`@Profile("!prod")`)
- Create the SUPER_ADMIN user manually via a one-time database insert or a secure bootstrap endpoint
- `COOKIE_SECURE=true` and `COOKIE_SAME_SITE=Strict` are required

---

## Stopping the Stack

```bash
# Stop without removing data
docker compose down

# Stop and remove all data (database volume included)
docker compose down -v
```
