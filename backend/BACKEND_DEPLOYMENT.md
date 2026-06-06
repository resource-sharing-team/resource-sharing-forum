# Backend Deployment Guide

This guide deploys the Spring Boot backend with MySQL 8 by Docker Compose. The API contract is unchanged: controllers keep the existing `/api` and `/api/v1` routes and response wrapper. The deployment target follows the design documents: Maven + JDK 17 + Docker.

For final assembly against `规范.docx` and the four PDF design documents, see `SPEC_DEPLOYMENT_ACCEPTANCE.md` and `BACKEND_DEPLOYMENT_TRACEABILITY.md`.

## Prerequisites

- Docker Engine with Docker Compose v2.
- Network access for the first Docker build to pull the Maven and Java base images and Maven dependencies.
- A strong JWT secret with at least 32 random characters.
- The exact frontend origin list for CORS, for example `http://localhost:5173`.

## Configure Environment

Create `backend/.env` from the example:

```powershell
cd D:\resource_sharing_forum\backend
Copy-Item .env.example .env
```

Edit `.env` before starting the stack:

```dotenv
MYSQL_ROOT_PASSWORD=<strong-root-password>
MYSQL_APP_USER=forum_app
MYSQL_APP_PASSWORD=<strong-app-password>
MYSQL_DATABASE=resource_sharing_forum
BACKEND_PORT=8080
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
JWT_SECRET=<at-least-32-random-characters>
DB_POOL_MIN_IDLE=10
DB_POOL_MAX_SIZE=50
AUTH_RATE_LIMIT_WINDOW_SECONDS=60
AUTH_RATE_LIMIT_MAX_REQUESTS=30
```

Do not commit `.env`. It is ignored by Git.

Validate `.env` before starting Compose:

```powershell
.\scripts\validate-env.ps1 -EnvFile .\.env
```

For the standard backend deployment flow, use:

```powershell
.\scripts\deploy.ps1 -EnvFile .\.env -Verify -BaseUrl http://localhost:8080 -Origin http://localhost:5173
```

The deploy script validates `.env`, checks that Docker Compose is available, runs `docker compose up -d --build`, waits for `/api/health` to report `data.database="UP"` when `-Verify` is supplied, and then runs the smoke verification script.

Deployment scripts resolve relative paths such as `.\.env`, `.\backups`, and `.\deployment-evidence` from the backend directory, not from the caller's current directory. Docker Compose commands also execute from the backend directory, so the same commands can be launched from a fresh PowerShell session after changing to `D:\resource_sharing_forum\backend`.

If the deployment host is slow during first MySQL/Flyway startup, pass `-ReadinessTimeoutSeconds 240` or a larger value to `deploy.ps1` or `verify-production-acceptance.ps1`.

For final production acceptance on a Docker-enabled host, use:

```powershell
.\scripts\verify-production-acceptance.ps1 -EnvFile .\.env -BaseUrl http://localhost:8080 -Origin http://localhost:5173 -FrontendDir ..\.worktrees\Web_User
```

This runs the Compose deployment, post-start smoke verification, evidence collection, and optional `Web_User` real-backend smoke against the deployed backend. When `-FrontendDir` is supplied, the frontend smoke result is written back into the same deployment acceptance summary.

When the backend runs with `SPRING_PROFILES_ACTIVE=prod`, startup fails if:

- `JWT_SECRET` is missing, still uses a placeholder/development value, or has fewer than 32 characters.
- `DB_PASSWORD` is missing, uses a placeholder/common weak value, or has fewer than 12 characters.
- `CORS_ALLOWED_ORIGINS` is missing, uses `*`, or contains non-HTTP origins.

## Start

```powershell
cd D:\resource_sharing_forum\backend
docker compose up -d --build
```

Compose starts:

- `mysql`: MySQL 8 with `utf8mb4` and Asia/Shanghai timezone.
- `backend`: Spring Boot app with `SPRING_PROFILES_ACTIVE=prod`, built by Maven on JDK 17 and run on a JDK 17 runtime.

The backend waits for the MySQL healthcheck before starting.

## Health Check

```powershell
curl http://localhost:8080/api/health
```

Expected response shape:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "UP",
    "database": "UP"
  },
  "timestamp": "..."
}
```

In the production Compose deployment, `database: "UP"` means the backend has successfully executed `SELECT 1` through the configured datasource. If the database check fails, the endpoint returns HTTP `503` with the same response wrapper and `data.status=DOWN`.

If `BACKEND_PORT` is changed in `.env`, use that port instead of `8080`.

## Deployment Smoke Verification

After the backend is running, execute:

```powershell
.\scripts\verify-deployment.ps1 -BaseUrl http://localhost:8080 -Origin http://localhost:5173
```

The script checks:

- `/api/health` unified response, database readiness, trace id, and the full security header set
- public `/api/v1/resources` pagination wrapper
- protected `/api/v1/user/profile` returns `401` without a token and still includes the full security header set
- CORS preflight for the configured frontend origin

To keep final deployment proof for assembly or review, collect evidence after the stack is running:

```powershell
.\scripts\collect-deployment-evidence.ps1 -EnvFile .\.env -BaseUrl http://localhost:8080 -Origin http://localhost:5173
```

The evidence collector validates `.env`, records `docker compose config`, `docker compose ps`, `/api/health`, deployment smoke output, a machine-readable `deployment-acceptance-summary.json`, and backend/MySQL log tails under `deployment-evidence\<timestamp>`. `verify-production-acceptance.ps1 -FrontendDir ...` also stores `frontend-smoke.txt` there and updates the summary with `frontendSmokeStatus`, `frontendSmokePassed`, and `frontendSmokeLog`.

`deployment-acceptance-summary.json` is written only after `/api/health` proves `data.status="UP"` and `data.database="UP"` and `verify-deployment.ps1` reports success. If frontend production smoke is requested and fails, the summary is rewritten with `passed=false` and `frontendSmokeStatus="FAILED"` before the script exits with failure.

Deployment scripts explicitly check native command exit codes for Docker Compose, backup/restore Docker commands, and frontend smoke npm commands. A failed external command fails the script instead of allowing later acceptance steps to continue.

## Frontend Integration Smoke Verification

For a local contract smoke that does not require Docker or MySQL, use:

```powershell
.\scripts\verify-frontend-integration.ps1 -FrontendDir ..\.worktrees\Web_User
```

The script starts the backend on `http://127.0.0.1:18080` with the test no-database configuration, waits for `/api/health`, then runs the `Web_User` real-backend Playwright smoke with `VITE_ENABLE_MOCKS=false`.

This verifies that the frontend calls the configured backend API base over real HTTP, receives the unchanged response wrapper, sees `X-Trace-Id`, and bypasses MSW. It is a local contract smoke and does not replace the Docker Compose production deployment verification above, which still requires `database="UP"`.

For a fuller local acceptance pass in the current Windows environment, use:

```powershell
.\scripts\verify-local-acceptance.ps1 -FrontendDir ..\.worktrees\Web_User
```

Before final submission, review `SPEC_DEPLOYMENT_ACCEPTANCE.md` to confirm each spec-derived deployment requirement has file, script, test, or runtime evidence.

This script intentionally does not call Docker. It runs backend Maven tests, frontend unit tests, frontend production build, frontend default e2e, the no-database backend plus `web_user` real-backend smoke, and whitespace checks for both worktrees. Run Docker Compose verification separately in a Docker-enabled deployment environment.

## Database Migration

The `prod` profile enables Flyway:

- migration path: `classpath:db/migration`
- baseline-on-migrate: `true`
- SQL init mode: `never`

On first startup, Flyway creates the V2 schema and seed data from the versioned migrations. The legacy `schema.sql` and `data.sql` remain for manual demonstration only and are not run automatically.

## Logs

```powershell
docker compose logs -f backend
docker compose logs -f mysql
```

Set `APP_LOG_LEVEL` in `.env` to adjust application logging, for example `DEBUG` during local diagnosis.

Console logs include structured fields for `timestamp`, `level`, `traceId`, `userId`, `class`, and `message`. The backend returns `X-Trace-Id` on each request and uses the same value in logs when available.

Global exception handling records recoverable business and validation failures as `WARN`, and records unexpected system failures as `ERROR` while preserving the unified API response wrapper. This matches the design requirement for production observability without changing frontend contracts.

## Database Pool

The production profile defaults the Hikari pool to the design-document recommendation:

- `DB_POOL_MIN_IDLE=10`
- `DB_POOL_MAX_SIZE=50`

Tune these values only when the deployment machine has lower resource limits.

## Sensitive Endpoint Rate Limit

Authentication-sensitive endpoints are protected by an in-memory rate limit for this course deployment:

- `AUTH_RATE_LIMIT_WINDOW_SECONDS=60`
- `AUTH_RATE_LIMIT_MAX_REQUESTS=30`

It covers login, registration, password-reset-code, and password-reset requests under both `/api/auth` and `/api/v1/auth`. Future Redis-backed distributed rate limiting can replace this when multiple backend instances are deployed.

## Frontend Integration

The backend allows browser calls only from origins configured by `CORS_ALLOWED_ORIGINS`. For a local frontend on Vite, keep:

```dotenv
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

For deployed frontend pages, replace those values with the production HTTPS origin. See `FRONTEND_INTEGRATION_GUIDE.md` and `API_CONTRACT.md` before wiring frontend API clients.

## Persistent Data

Compose creates two named volumes:

- `resource-sharing-forum_mysql_data`: database files.
- `resource-sharing-forum_backend_uploads`: uploaded file storage mounted at `/app/uploads`.

The Compose file sets explicit volume names so backup and restore scripts use stable storage targets even when Compose is invoked from a different directory or with a different project name.

To stop without deleting data:

```powershell
docker compose down
```

Only use `docker compose down -v` when intentionally deleting database and upload data.

## Backup And Restore

The design documents require daily database backup and periodic file backup. A simple MySQL backup command is:

```powershell
docker compose exec mysql mysqldump -u root -p resource_sharing_forum > backup-resource-sharing-forum.sql
```

Keep at least the latest seven daily database backups. Back up the upload volume or `/app/uploads` contents on the same schedule as deployment operations require. Perform a restore drill before relying on the backup plan for production data.

Reusable scripts are provided:

```powershell
.\scripts\deploy.ps1 -EnvFile .\.env -Verify -BaseUrl http://localhost:8080 -Origin http://localhost:5173
.\scripts\backup.ps1 -EnvFile .\.env -OutputDir .\backups
.\scripts\restore.ps1 -EnvFile .\.env -DatabaseBackup .\backups\resource_sharing_forum-YYYYMMDD-HHMMSS.sql
.\scripts\validate-env.ps1 -EnvFile .\.env
.\scripts\verify-deployment.ps1 -BaseUrl http://localhost:8080 -Origin http://localhost:5173
.\scripts\collect-deployment-evidence.ps1 -EnvFile .\.env -BaseUrl http://localhost:8080 -Origin http://localhost:5173
.\scripts\verify-production-acceptance.ps1 -EnvFile .\.env -BaseUrl http://localhost:8080 -Origin http://localhost:5173 -FrontendDir ..\.worktrees\Web_User
.\scripts\verify-frontend-integration.ps1 -FrontendDir ..\.worktrees\Web_User
.\scripts\verify-local-acceptance.ps1 -FrontendDir ..\.worktrees\Web_User
```

Script paths: `scripts/deploy.ps1`, `scripts/backup.ps1`, `scripts/restore.ps1`, `scripts/validate-env.ps1`, `scripts/verify-deployment.ps1`, `scripts/deployment-summary.ps1`, `scripts/collect-deployment-evidence.ps1`, `scripts/verify-production-acceptance.ps1`, `scripts/verify-frontend-integration.ps1`, `scripts/verify-local-acceptance.ps1`.

`backup.ps1` and `restore.ps1` validate and import `.env` automatically before calling Docker, so they can be run from a fresh PowerShell session after the deployment file is configured.

If Docker is unavailable after `.env` and path validation, backup and restore fail with an explicit Docker requirement message instead of a raw command failure.

Relative backup, restore, and evidence paths are resolved from the backend directory. Absolute paths are preserved.

When `backup.ps1` or `restore.ps1` imports `.env`, values from the supplied file override any existing variables in the current PowerShell process. This prevents stale `MYSQL_DATABASE` or `MYSQL_ROOT_PASSWORD` values from a previous deployment session from leaking into backup or restore commands.

When restoring uploaded files, pass `-UploadBackup` with the tar file produced by the backup script.

`restore.ps1` resolves database and upload backup paths before calling Docker, so both relative and absolute backup paths are supported and missing backup files fail fast before restore commands run.

## Security Headers

The backend writes deployment security headers on API responses:

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy` disabling camera, microphone, and geolocation
- `Content-Security-Policy` with `frame-ancestors 'none'`

## Update Deployment

After pulling or editing backend code:

```powershell
cd D:\resource_sharing_forum\backend
docker compose up -d --build
docker compose logs -f backend
```

Flyway applies any new migrations during application startup.

## CI Gate

GitHub Actions workflow `.github/workflows/backend-ci.yml` runs the backend deployment gate on pushes and pull requests:

- JDK 17 Maven tests with `./mvnw -B test`
- `git diff --check`
- `docker compose config`
- Docker image build for the backend

The CI workflow creates a temporary `.env` with strong dummy values so Compose can be validated without committing deployment secrets. Real deployment still requires operator-provided `.env` values.

## Security Notes

- Replace all example passwords and `JWT_SECRET` before any shared or production deployment.
- Run `scripts/validate-env.ps1` before startup; the production profile also rejects unsafe JWT, database password, and CORS values during startup.
- Keep `.env` out of Git.
- Set `CORS_ALLOWED_ORIGINS` to explicit trusted frontend origins.
- Put HTTPS and public routing behind a reverse proxy or gateway.
- The current deployment does not add Redis token blacklist, email delivery, object storage, WebSocket, or async notification queues; those remain future integration work.

## Local Verification Before Deployment

```powershell
cd D:\resource_sharing_forum\backend
.\mvnw.cmd -s D:\tmp\maven-aliyun-settings.xml test
git diff --check
.\scripts\validate-env.ps1 -EnvFile .\.env
.\scripts\deploy.ps1 -EnvFile .\.env -Verify -BaseUrl http://localhost:8080 -Origin http://localhost:5173
docker compose config
.\scripts\verify-deployment.ps1 -BaseUrl http://localhost:8080 -Origin http://localhost:5173
.\scripts\collect-deployment-evidence.ps1 -EnvFile .\.env -BaseUrl http://localhost:8080 -Origin http://localhost:5173
.\scripts\verify-production-acceptance.ps1 -EnvFile .\.env -BaseUrl http://localhost:8080 -Origin http://localhost:5173 -FrontendDir ..\.worktrees\Web_User
.\scripts\verify-frontend-integration.ps1 -FrontendDir ..\.worktrees\Web_User
.\scripts\verify-local-acceptance.ps1 -FrontendDir ..\.worktrees\Web_User
```
