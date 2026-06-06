# Backend Deployment Traceability

This checklist maps the design documents and backend deployment requirements to current implementation evidence. It is intentionally concise so it can be used during review or final project assembly.

## Scope

- Documents referenced by the user: detailed design, UML modeling, coding standard, and database design PDFs.
- Local `规范.docx` is also used as the service-layer/facade acceptance source.
- Backend deployment target: Spring Boot backend + MySQL 8 with Docker Compose.
- Design document runtime target: Maven + JDK 17 + Docker.
- API compatibility target: existing Controller routes, `/api` and `/api/v1`, response wrapper, pagination shape, and `Map<String, Object>` response compatibility remain unchanged.
- Detailed spec deployment acceptance is recorded in `SPEC_DEPLOYMENT_ACCEPTANCE.md`.

## Traceability Matrix

| Requirement Area | Deployment / Implementation Evidence | Verification Evidence |
| --- | --- | --- |
| API contract remains unchanged | Controllers still expose existing `/api` and `/api/v1` routes; deployment guide states API contract is unchanged. | `ApiSmokeTests`; `BACKEND_DEPLOYMENT.md` |
| Facade boundary remains in place | `DesignSpecForumService` remains controller-compatible facade and does not depend on JDBC, transactions, state machines, or `PointManager`. | `DesignSpecFacadeStructureTest` |
| Database design is deployed by migrations | Flyway uses `classpath:db/migration`; SQL init is disabled to avoid duplicate schema/data imports. | `application-prod.yml`; `DesignSpecMySqlIntegrationTests` when Docker is available |
| MySQL 8 deployment target | Compose defines `mysql:8.0`, server-side `utf8mb4`, Asia/Shanghai timezone, a Connector/J URL without invalid Java charset aliases, and explicit persistent `resource-sharing-forum_mysql_data` volume. | `docker-compose.yml`; `BackendDeploymentConfigTest` |
| Production database pool follows design guidance | Prod Hikari defaults are `minimum-idle=10` and `maximum-pool-size=50`, with env overrides for constrained hosts. | `application-prod.yml`; `.env.example`; `BackendDeploymentConfigTest` |
| Backend container deployment | Dockerfile uses Maven/JDK 17 build stage, JDK 17 runtime, non-root `app` user, `/api/health` healthcheck. | `Dockerfile`; `pom.xml`; `BackendDeploymentConfigTest` |
| CI deployment gate is available | GitHub Actions runs JDK 17 Maven tests, packages the backend jar, runs whitespace checks, validates Compose with a temporary strong dummy `.env`, builds the Docker image for backend changes, uploads a `backend-deployment-handoff` artifact containing the jar, Maven metadata, and `.env.example` but no real `.env` secrets, and a separate frontend-backend smoke job checks out `Web_User`, starts the backend in no-database smoke mode, and runs the frontend real-backend e2e against the configured API base. | `.github/workflows/backend-ci.yml`; `.github/workflows/frontend-integration-ci.yml`; `BackendDeploymentConfigTest`; `BACKEND_DEPLOYMENT.md`; `FRONTEND_INTEGRATION_GUIDE.md`; `scripts/verify-frontend-integration.ps1` |
| Production configuration is environment-driven | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, upload limits, pool settings, and log level are environment variables. | `application-prod.yml`; `BackendDeploymentConfigTest` |
| Frontend integration CORS is explicit | `CORS_ALLOWED_ORIGINS` is required in Compose/prod and registered for `/api/**` in security configuration. | `application-prod.yml`; `docker-compose.yml`; `SecurityConfig`; `BackendDeploymentConfigTest`; `FRONTEND_INTEGRATION_GUIDE.md` |
| Sensitive auth endpoints are rate limited | Login, registration, and password reset endpoints have configurable in-memory rate limiting for the single-instance course deployment. | `RateLimitFilter`; `RateLimitFilterTest`; `BackendDeploymentConfigTest` |
| Security headers are applied | API responses include content-type, frame, referrer, permissions, and CSP headers similar to the coding-standard security-header requirement; deployment smoke verification checks the same full header set on public and protected API responses. | `SecurityHeadersFilter`; `SecurityHeadersFilterTest`; `scripts/verify-deployment.ps1`; `BackendDeploymentConfigTest` |
| Production secrets are not committed | `.env` is ignored; `.env.example` documents required values; pre-start script rejects placeholder/weak Compose secrets, and `prod` profile rejects unsafe `JWT_SECRET`, weak database passwords, and invalid CORS origins. | `.gitignore`; `.env.example`; `scripts/validate-env.ps1`; `ProductionDeploymentConfigTest`; `BackendDeploymentConfigTest` |
| Upload storage persists across container restarts | Compose maps explicit volume `resource-sharing-forum_backend_uploads` to `/app/uploads`; prod profile points uploads to `/app/uploads`; backup/restore scripts use the same stable volume name. | `docker-compose.yml`; `application-prod.yml`; `scripts/backup.ps1`; `scripts/restore.ps1`; `BackendDeploymentConfigTest` |
| Health endpoint is available for deployment checks | `/api/health` is public, preserves the unified response wrapper, runs `SELECT 1` when a datasource is configured, returns HTTP `503` on database failure, and the deployment smoke script requires `database=UP` before frontend handoff. | `HealthController`; `SecurityConfig`; `ApiSmokeTests`; `HealthControllerTest`; `scripts/verify-deployment.ps1`; `BackendDeploymentConfigTest` |
| Structured logs include traceId, userId, and exception severity | `TraceIdFilter` puts request trace ids into MDC and response header; JWT auth puts authenticated user id into MDC; global exception handling records business/validation failures as `WARN` and unexpected failures as `ERROR`; `logback-spring.xml` prints timestamp, level, traceId, userId, class, and message fields. | `TraceIdFilter`; `JwtAuthenticationFilter`; `GlobalExceptionHandler`; `logback-spring.xml`; `GlobalExceptionHandlerTest`; `BackendDeploymentConfigTest` |
| Notification event boundary follows spec layering | Dispatcher creates notification events, writes station notices, marks `SENT`/`FAILED`, and does not roll back core business. | `NotificationDispatcher`; `NotificationEventService`; `NotificationDispatcherTest` |
| Admin/log/notification side effects remain service-layer responsibilities | Migrated audit/report/member/resource services use module services and dispatcher/log services rather than controllers. | `DesignSpecFacadeStructureTest`; service package implementations |
| Local acceptance verification is reproducible in this environment | Maven tests, frontend tests/build/e2e, diff checks, and no-database backend plus `web_user` real-backend contract smoke are runnable without Docker; Docker-specific checks are documented as deployment-environment gates. | `BACKEND_IMPLEMENTATION_STATUS.md`; `BACKEND_DEPLOYMENT.md`; `scripts/verify-local-acceptance.ps1`; `scripts/verify-frontend-integration.ps1`; `web_user` `test:e2e:backend` |
| Deploy, backup, restore, environment validation, production acceptance, evidence collection, and smoke verification scripts are available | Deploy script validates the supplied `.env`, checks Docker Compose availability, runs Compose build/start with `--env-file`, waits for `/api/health` to report `data.database=UP`, and invokes smoke verification; readiness timeout is configurable for slow first startup; database backup uses `mysqldump`, upload backup archives the upload volume, retention defaults to seven days, backup/restore validate and import `.env`, imported `.env` values override stale process variables, backup/restore fail clearly when Docker is unavailable after validation, restore resolves database/upload backup paths before Docker execution, restore script replays SQL with `--env-file` and restores upload tar, environment validation rejects unsafe `.env`, production acceptance composes deploy, smoke verification, evidence collection, and optional frontend real-backend smoke, deployment smoke verification checks health/API/CORS plus the full deployment security header set after deployment, evidence collection records Compose config/status, health response, smoke output, machine-readable acceptance summary with `healthDatabase=UP`, frontend smoke status/log fields when frontend smoke is requested, and service log tails, frontend integration verification starts a local no-database backend smoke before running `web_user` real-backend e2e, local acceptance verification groups all current-environment checks without calling Docker, deployment scripts resolve relative paths from the backend directory while preserving absolute paths, native Docker/Compose/npm command failures are converted into explicit script failures, and `deployment-summary.ps1` centralizes summary generation without evidence-collection side effects. | `scripts/deploy.ps1`; `scripts/backup.ps1`; `scripts/restore.ps1`; `scripts/validate-env.ps1`; `scripts/verify-deployment.ps1`; `scripts/deployment-summary.ps1`; `scripts/collect-deployment-evidence.ps1`; `scripts/verify-production-acceptance.ps1`; `scripts/verify-frontend-integration.ps1`; `scripts/verify-local-acceptance.ps1`; `BackendDeploymentConfigTest`; `DeploymentScriptsTest` |
| Database and frontend handoff documents exist | API response contract, frontend CORS/token/download notes, `web_user` mock-to-backend switching steps, deployment smoke command, frontend real-backend e2e command, and schema summary are recorded for assembly. | `API_CONTRACT.md`; `FRONTEND_INTEGRATION_GUIDE.md`; `SCHEMA.md`; `BackendDeploymentConfigTest`; `web_user` `test:e2e:backend` |
| Spec deployment acceptance is mapped to evidence | `规范.docx` plus the four PDFs are mapped to runtime, API, database, security, logging, backup, and frontend handoff evidence without changing public contracts. | `SPEC_DEPLOYMENT_ACCEPTANCE.md`; `BackendDeploymentConfigTest` |

## Local Verification Status

Runnable in the current Windows PowerShell environment:

```powershell
cd D:\resource_sharing_forum\backend
.\mvnw.cmd -s D:\tmp\maven-aliyun-settings.xml test
.\scripts\verify-local-acceptance.ps1 -FrontendDir ..\.worktrees\Web_User
cd D:\resource_sharing_forum
git diff --check
```

Requires a Docker-enabled environment:

```powershell
cd D:\resource_sharing_forum\backend
docker compose config
docker compose up -d --build
curl http://localhost:8080/api/health
```

## Known Environment Limitation

The current local environment does not provide the `docker` command, so Compose expansion, image build, container startup, and runtime health verification must be executed on a Docker-enabled machine. File-level deployment regression tests cover the Compose/Dockerfile/prod-config contract until that environment is available.
