# Spec Deployment Acceptance Matrix

This matrix maps `规范.docx` and the four local PDF design documents to the current backend deployment and frontend handoff evidence. It is written for final project assembly and review; it does not add or change API routes.

## Source Documents

| Document | Deployment-Relevant Requirements Used |
| --- | --- |
| `规范.docx` | Keep Controllers unchanged, keep `DesignSpecForumService` as a facade, preserve `/api/v1`, preserve frontend integration, route state/points/logs/notifications through their module services, and do not expose real file paths. |
| `详细设计说明书.pdf` | Runtime stack is Maven + JDK 17 + Docker, database is MySQL 8.x, auth is Spring Security + JWT, frontend and backend are separated, downloads must pass backend authorization, response format and pagination stay unified, pool guidance is min 10/max 50, Flyway or Liquibase manages schema versions, logs and backup/restore are required. |
| `资源分享论坛——UML建模文档.pdf` | User/admin flows require login and permission checks, resource download rejects unavailable resources, resource lifecycle states close or open the frontend download entry, and admin operations are guarded. |
| `资源分享论坛——编码规范文档.pdf` | API data stays in the frontend `api/` layer, backend responses use a unified format, pagination returns total/list, security headers and CORS are configured, sensitive endpoints are rate limited, JWT protects non-public routes, logs are standardized, and MySQL 8.x uses UTF-8 compatible storage. |
| `资源分享论坛——数据库设计说明书.pdf` | 35 core tables across 9 modules, state-machine controlled records, notification event plus station notice model, file metadata-only storage, backend-authorized downloads, download records/statistics, password hashing, no arbitrary frontend status updates, admin logs, and database acceptance checks. |

## Acceptance Matrix

| Requirement | Current Evidence | Verification |
| --- | --- | --- |
| Runtime target is Maven + JDK 17 + Docker. | `pom.xml` sets Java 17; `Dockerfile` uses Maven/JDK 17 build and JDK 17 runtime; `docker-compose.yml` runs the backend container. | `BackendDeploymentConfigTest.dockerImageUsesJava17HealthcheckAndNonRootRuntime`; GitHub Actions backend CI. |
| Production database target is MySQL 8.x with `utf8mb4`. | `docker-compose.yml` uses `mysql:8.0`, `--character-set-server=utf8mb4`, `--collation-server=utf8mb4_unicode_ci`, a Connector/J URL without invalid Java charset aliases, and explicit persistent `resource-sharing-forum_mysql_data`; `SCHEMA.md` records MySQL 8.x and `utf8mb4`. | `BackendDeploymentConfigTest.dockerComposeDefinesBackendMysqlAndPersistentUploads`; Docker Compose verification on a Docker host. |
| Schema is version managed and aligned with the database design. | Flyway points to `classpath:db/migration`; migrations define 35 core tables, status checks, foreign keys, full-text indexes, download records, point flow, admin logs, notification events, and system notices. | `BackendDeploymentConfigTest.prodProfileUsesEnvironmentDrivenSecretsAndFlyway`; `DesignSpecMySqlIntegrationTests` when Docker is available. |
| Controller and public API contract remain compatible. | Controllers still call the facade; `/api` and `/api/v1` compatibility paths are documented; response wrapper and pagination are unchanged; `Map<String,Object>` remains. | `ApiSmokeTests`; `DesignSpecFacadeStructureTest`; `API_CONTRACT.md`; frontend real-backend e2e. |
| Facade/layering follows `Controller -> Facade -> module service`. | `DesignSpecForumService` depends on module services, not JDBC, transactions, state machines, `PointManager`, or legacy implementation. | `DesignSpecFacadeStructureTest`. |
| Resource, request, point, notification, and admin-log rules remain traceable. | Status logs, point flow, `AdminLogService`, `NotificationDispatcher`, `notification_event`, and `system_notice` are implemented and documented. | State-machine tests, `NotificationDispatcherTest`, `DesignSpecFacadeStructureTest`, `SCHEMA.md`. |
| Security guardrails are deployment-ready. | Spring Security + JWT, explicit CORS allowlist, security headers, production secret guard, bcrypt password storage, and auth endpoint rate limiting are present. | `BackendDeploymentConfigTest`; `ProductionDeploymentConfigTest`; `SecurityHeadersFilterTest`; `RateLimitFilterTest`. |
| File path and download security are preserved. | Frontend guide forbids consuming `storage_path`; API contract says real paths are not exposed; database stores metadata/path server-side; download endpoints remain backend-mediated. | `API_CONTRACT.md`; `FRONTEND_INTEGRATION_GUIDE.md`; resource/file service tests and smoke tests. |
| Logging and observability meet the coding/design requirements. | `TraceIdFilter` writes and returns `X-Trace-Id`; JWT auth adds `userId` to MDC; global exception handling logs WARN/ERROR; `logback-spring.xml` emits structured console fields. | `BackendDeploymentConfigTest.deploymentLoggingIncludesTraceIdAndStructuredFields`; `GlobalExceptionHandlerTest`; deployment smoke checks headers. |
| Backup, restore, environment validation, production acceptance, evidence collection, and smoke verification are reusable. | `deploy.ps1`, `backup.ps1`, `restore.ps1`, `validate-env.ps1`, `verify-deployment.ps1`, `deployment-summary.ps1`, `collect-deployment-evidence.ps1`, `verify-production-acceptance.ps1`, `verify-frontend-integration.ps1`, and `verify-local-acceptance.ps1` are documented; Compose scripts use the supplied `--env-file`; deployment verification waits for `/api/health` database readiness instead of using a fixed sleep; backup/restore target stable explicit volume names; relative script paths resolve from the backend directory while absolute paths are preserved; backup/restore `.env` import overrides stale process variables; native Docker/Compose/npm command failures become explicit script failures; evidence collection writes `deployment-acceptance-summary.json` only when health and smoke verification prove database readiness; production acceptance records frontend smoke status and log in the same summary when `-FrontendDir` is supplied; backend CI uploads a deployment handoff artifact containing the backend jar, Maven metadata, deployment files, `.env.example`, and docs without real `.env` secrets. | `DeploymentScriptsTest`; `BackendDeploymentConfigTest.securityHeadersAndBackupScriptsAreDeployableArtifacts`; `BackendDeploymentConfigTest.githubActionsWorkflowRunsBackendDeploymentGates`. |
| Frontend `Web_User` can switch from MSW to backend. | Frontend client supports `VITE_API_BASE_URL`, `VITE_API_PREFIX`, `VITE_ENABLE_MOCKS=false`, unwraps backend response envelopes, normalizes backend pagination, and bypasses MSW in backend mode. | `web_user` unit tests; `test:e2e:backend`; `scripts/verify-frontend-integration.ps1`; frontend integration CI. |
| Current no-Docker environment has a reproducible local acceptance gate. | `verify-local-acceptance.ps1` runs backend tests, frontend tests/build/e2e, real-backend no-database smoke, and diff checks without calling Docker. | Latest local run passed; `BACKEND_DEPLOYMENT.md`; `BACKEND_DEPLOYMENT_TRACEABILITY.md`. |

## External Deployment Gate

The current machine does not provide Docker, so full production Compose startup remains an external-environment gate. On a Docker-enabled host, run:

```powershell
cd D:\resource_sharing_forum\backend
.\scripts\validate-env.ps1 -EnvFile .\.env
docker compose config
docker compose up -d --build
.\scripts\verify-deployment.ps1 -BaseUrl http://localhost:8080 -Origin http://localhost:5173
.\scripts\collect-deployment-evidence.ps1 -EnvFile .\.env -BaseUrl http://localhost:8080 -Origin http://localhost:5173
.\scripts\verify-production-acceptance.ps1 -EnvFile .\.env -BaseUrl http://localhost:8080 -Origin http://localhost:5173 -FrontendDir ..\.worktrees\Web_User
```

Completion evidence for that gate is `/api/health` returning the unified wrapper with `data.status="UP"` and `data.database="UP"`, `verify-deployment.ps1` passing, and `deployment-evidence\<timestamp>\deployment-acceptance-summary.json` containing `passed=true`, `healthDatabase="UP"`, and, when `-FrontendDir` is supplied, `frontendSmokeStatus="PASSED"`.
