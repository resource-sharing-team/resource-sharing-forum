package com.resourcesharing.forum;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class BackendDeploymentConfigTest {
    @Test
    void prodProfileUsesEnvironmentDrivenSecretsAndFlyway() throws IOException {
        String prodConfig = read("src/main/resources/application-prod.yml");
        String prodGuard = read("src/main/java/com/resourcesharing/forum/config/ProductionDeploymentConfig.java");

        assertThat(prodConfig).contains(
                "url: ${DB_URL}",
                "username: ${DB_USERNAME}",
                "password: ${DB_PASSWORD}",
                "allowed-origins: ${CORS_ALLOWED_ORIGINS}",
                "secret: ${JWT_SECRET}",
                "enabled: ${FLYWAY_ENABLED:true}",
                "locations: classpath:db/migration",
                "mode: never",
                "root-dir: ${UPLOAD_ROOT_DIR:/app/uploads}",
                "max-file-size: ${UPLOAD_MAX_FILE_SIZE:100MB}",
                "max-request-size: ${UPLOAD_MAX_REQUEST_SIZE:500MB}",
                "minimum-idle: ${DB_POOL_MIN_IDLE:10}",
                "maximum-pool-size: ${DB_POOL_MAX_SIZE:50}"
        );
        assertThat(prodConfig).doesNotContain("resource-sharing-forum-dev-secret-must-be-changed-2026");
        assertThat(prodGuard).contains(
                "@Profile(\"prod\")",
                "JWT_SECRET is required",
                "must contain at least 32 characters",
                "DB_PASSWORD is required",
                "DB_PASSWORD must be replaced",
                "CORS_ALLOWED_ORIGINS is required",
                "must not use wildcard origins",
                "must contain HTTP(S) origins"
        );
    }

    @Test
    void dockerComposeDefinesBackendMysqlAndPersistentUploads() throws IOException {
        String compose = read("docker-compose.yml");

        assertThat(compose).contains(
                "mysql:",
                "backend:",
                "condition: service_healthy",
                "SPRING_PROFILES_ACTIVE: prod",
                "jdbc:mysql://mysql:3306/",
                "--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_unicode_ci",
                "CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS:?Set CORS_ALLOWED_ORIGINS in .env}",
                "JWT_SECRET: ${JWT_SECRET:?Set JWT_SECRET in .env}",
                "UPLOAD_MAX_FILE_SIZE_MB: ${UPLOAD_MAX_FILE_SIZE_MB:-100}",
                "UPLOAD_MAX_FILES_PER_RESOURCE: ${UPLOAD_MAX_FILES_PER_RESOURCE:-5}",
                "UPLOAD_MAX_FILE_SIZE: ${UPLOAD_MAX_FILE_SIZE:-100MB}",
                "UPLOAD_MAX_REQUEST_SIZE: ${UPLOAD_MAX_REQUEST_SIZE:-500MB}",
                "DB_POOL_MIN_IDLE: ${DB_POOL_MIN_IDLE:-10}",
                "DB_POOL_MAX_SIZE: ${DB_POOL_MAX_SIZE:-50}",
                "AUTH_RATE_LIMIT_WINDOW_SECONDS: ${AUTH_RATE_LIMIT_WINDOW_SECONDS:-60}",
                "AUTH_RATE_LIMIT_MAX_REQUESTS: ${AUTH_RATE_LIMIT_MAX_REQUESTS:-30}",
                "backend_uploads:/app/uploads",
                "backend_uploads:",
                "name: resource-sharing-forum_mysql_data",
                "name: resource-sharing-forum_backend_uploads"
        );
        assertThat(compose)
                .contains("MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?Set MYSQL_ROOT_PASSWORD in .env}")
                .doesNotContain("MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root}")
                .doesNotContain("characterEncoding=utf8mb4");
    }

    @Test
    void dockerImageUsesJava17HealthcheckAndNonRootRuntime() throws IOException {
        String dockerfile = read("Dockerfile");
        String pom = read("pom.xml");
        String healthController = read("src/main/java/com/resourcesharing/forum/controller/HealthController.java");

        assertThat(dockerfile).contains(
                "FROM maven:3.9.9-eclipse-temurin-17 AS build",
                "FROM eclipse-temurin:17-jre-jammy",
                "SPRING_PROFILES_ACTIVE=prod",
                "curl -fsS http://127.0.0.1:${SERVER_PORT:-8080}/api/health",
                "USER app"
        );
        assertThat(pom).contains("<java.version>17</java.version>");
        assertThat(healthController).contains(
                "ObjectProvider<JdbcTemplate>",
                "SELECT 1",
                "database\", \"UP\"",
                "database\", \"DOWN\"",
                "HttpStatus.SERVICE_UNAVAILABLE"
        );
    }

    @Test
    void securityConfigUsesEnvironmentDrivenCorsForFrontendIntegration() throws IOException {
        String securityConfig = read("src/main/java/com/resourcesharing/forum/config/SecurityConfig.java");
        String appConfig = read("src/main/resources/application.yml");

        assertThat(securityConfig).contains(
                ".cors(Customizer.withDefaults())",
                ".httpBasic(AbstractHttpConfigurer::disable)",
                ".formLogin(AbstractHttpConfigurer::disable)",
                "CorsConfigurationSource",
                "UserDetailsService",
                "registerCorsConfiguration(\"/api/**\", configuration)",
                "\"X-Trace-Id\"",
                "setAllowCredentials(true)"
        );
        assertThat(appConfig).contains(
                "forum:",
                "cors:",
                "allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173"
        );
    }

    @Test
    void deploymentLoggingIncludesTraceIdAndStructuredFields() throws IOException {
        String traceFilter = read("src/main/java/com/resourcesharing/forum/config/TraceIdFilter.java");
        String jwtFilter = read("src/main/java/com/resourcesharing/forum/security/JwtAuthenticationFilter.java");
        String exceptionHandler = read("src/main/java/com/resourcesharing/forum/common/GlobalExceptionHandler.java");
        String logback = read("src/main/resources/logback-spring.xml");

        assertThat(traceFilter).contains(
                "@Order(Ordered.HIGHEST_PRECEDENCE)",
                "MDC.put(\"traceId\", traceId)",
                "MDC.remove(\"traceId\")",
                "X-Trace-Id"
        );
        assertThat(jwtFilter).contains(
                "MDC.put(\"userId\", claims.subject())",
                "MDC.remove(\"userId\")"
        );
        assertThat(exceptionHandler).contains(
                "LoggerFactory.getLogger(GlobalExceptionHandler.class)",
                "log.warn(\"Business exception handled",
                "log.warn(\"Validation exception handled",
                "log.error(\"System exception handled\", exception)"
        );
        assertThat(logback).contains(
                "\"timestamp\"",
                "\"level\"",
                "\"traceId\"",
                "\"userId\"",
                "\"class\"",
                "\"message\"",
                "APP_LOG_LEVEL"
        );
    }

    @Test
    void sensitiveAuthEndpointsHaveConfigurableRateLimit() throws IOException {
        String filter = read("src/main/java/com/resourcesharing/forum/config/RateLimitFilter.java");
        String prodConfig = read("src/main/resources/application-prod.yml");

        assertThat(filter).contains(
                "@Order(Ordered.HIGHEST_PRECEDENCE + 2)",
                "isSensitiveAuthRequest",
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/reset-password",
                "response.setStatus(429)"
        );
        assertThat(prodConfig).contains(
                "auth-window-seconds: ${AUTH_RATE_LIMIT_WINDOW_SECONDS:60}",
                "auth-max-requests: ${AUTH_RATE_LIMIT_MAX_REQUESTS:30}"
        );
    }

    @Test
    void securityHeadersAndBackupScriptsAreDeployableArtifacts() throws IOException {
        String headersFilter = read("src/main/java/com/resourcesharing/forum/config/SecurityHeadersFilter.java");
        String deploy = read("scripts/deploy.ps1");
        String backup = read("scripts/backup.ps1");
        String restore = read("scripts/restore.ps1");
        String validateEnv = read("scripts/validate-env.ps1");
        String verify = read("scripts/verify-deployment.ps1");
        String deploymentSummary = read("scripts/deployment-summary.ps1");
        String collectEvidence = read("scripts/collect-deployment-evidence.ps1");
        String productionAcceptance = read("scripts/verify-production-acceptance.ps1");
        String verifyFrontend = read("scripts/verify-frontend-integration.ps1");
        String verifyLocal = read("scripts/verify-local-acceptance.ps1");

        assertThat(headersFilter).contains(
                "@Order(Ordered.HIGHEST_PRECEDENCE + 1)",
                "X-Content-Type-Options",
                "X-Frame-Options",
                "Content-Security-Policy",
                "Permissions-Policy"
        );
        assertThat(deploy).contains(
                "EnvFile = \"./.env\"",
                "ReadinessTimeoutSeconds = 120",
                "Resolve-BackendPath",
                "$BackendRoot",
                "Wait-BackendReadiness",
                "$BaseUrl/api/health",
                "database=UP",
                "Backend did not become ready with database=UP",
                "Assert-LastExitCode",
                "Docker Compose deployment",
                "validate-env.ps1",
                "Assert-DockerComposeAvailable",
                "Get-Command docker",
                "docker compose version",
                "Push-Location $BackendRoot",
                "docker compose --env-file $envPath up -d --build",
                "verify-deployment.ps1",
                "Backend deployment command completed"
        );
        assertThat(deploy).doesNotContain("Start-Sleep -Seconds 10");
        assertThat(backup).contains(
                "EnvFile = \"./.env\"",
                "Resolve-BackendPath",
                "$outputPath = Resolve-BackendPath $OutputDir",
                "Import-EnvFile",
                "[Environment]::SetEnvironmentVariable($key, $value, \"Process\")",
                "Assert-DockerAvailable",
                "Docker command is required for backup",
                "Assert-LastExitCode",
                "Database backup",
                "Upload backup",
                "validate-env.ps1",
                "mysqldump",
                "docker compose --env-file $envPath exec -T mysql mysqldump",
                "-v \"${outputPath}:/backup\"",
                "backend-uploads",
                "RetentionDays = 7"
        );
        assertThat(restore).contains(
                "EnvFile = \"./.env\"",
                "Resolve-BackendPath",
                "$databaseBackupCandidate = Resolve-BackendPath $DatabaseBackup",
                "Import-EnvFile",
                "[Environment]::SetEnvironmentVariable($key, $value, \"Process\")",
                "Assert-DockerAvailable",
                "Docker command is required for restore",
                "Assert-LastExitCode",
                "Database restore",
                "Upload restore",
                "validate-env.ps1",
                "Resolve-Path -LiteralPath $databaseBackupCandidate",
                "Resolve-Path -LiteralPath $uploadBackupCandidate",
                "docker compose --env-file $envPath exec -T mysql mysql",
                "${uploadBackupDir}:/restore:ro",
                "mysql -u root",
                "tar -xf",
                "Restore completed"
        );
        assertThat(backup).doesNotContain("if (-not [Environment]::GetEnvironmentVariable($key, \"Process\"))");
        assertThat(restore).doesNotContain("if (-not [Environment]::GetEnvironmentVariable($key, \"Process\"))");
        assertThat(validateEnv).contains(
                "MYSQL_ROOT_PASSWORD",
                "MYSQL_APP_PASSWORD",
                "JWT_SECRET",
                "CORS_ALLOWED_ORIGINS",
                "change-this",
                "replace-with",
                "must not use wildcard origins",
                "Environment validation passed"
        );
        assertThat(verify).contains(
                "SkipMain",
                "Invoke-WebRequestCompat",
                "SkipHttpErrorCheck",
                "Get-HeaderValue",
                "[System.Net.WebHeaderCollection]",
                "/api/health",
                "$healthJson.data.database -ne \"UP\"",
                "/api/v1/resources",
                "/api/v1/user/profile",
                "Assert-SecurityHeaders",
                "X-Trace-Id",
                "X-Content-Type-Options",
                "X-Frame-Options",
                "Referrer-Policy",
                "Permissions-Policy",
                "Content-Security-Policy",
                "Access-Control-Allow-Origin",
                "Deployment verification passed"
        );
        assertThat(deploymentSummary).contains(
                "function Save-AcceptanceSummary",
                "deployment-acceptance-summary.json",
                "FrontendSmokeStatus",
                "frontendSmokeStatus",
                "frontendSmokePassed",
                "frontendSmokeLog",
                "frontend smoke must pass when required"
        );
        assertThat(collectEvidence).contains(
                "EnvFile = \"./.env\"",
                "OutputDir = \"./deployment-evidence\"",
                "EvidencePathFile",
                "Resolve-BackendPath",
                "deployment-summary.ps1",
                "Join-Path $PSScriptRoot \"deployment-summary.ps1\"",
                "$outputRoot = Resolve-BackendPath $OutputDir",
                "Save-AcceptanceSummary",
                "Assert-LastExitCode",
                "validate-env.ps1",
                "Docker command is required to collect deployment evidence",
                "Push-Location $BackendRoot",
                "docker compose --env-file $envPath config",
                "docker compose --env-file $envPath ps",
                "$BaseUrl/api/health",
                "verify-deployment.ps1",
                "docker compose --env-file $envPath logs --tail 200 backend",
                "docker compose --env-file $envPath logs --tail 100 mysql",
                "Deployment evidence collected at"
        );
        assertThat(productionAcceptance).contains(
                "EnvFile = \"./.env\"",
                "EvidenceDir = \"./deployment-evidence\"",
                "ReadinessTimeoutSeconds = 120",
                "Resolve-BackendPath",
                "Join-Path $PSScriptRoot \"deployment-summary.ps1\"",
                "$envPath = Resolve-BackendPath $EnvFile",
                "$evidencePath = Resolve-BackendPath $EvidenceDir",
                "$evidencePathFile = Join-Path $evidencePath \".latest-production-evidence-path.txt\"",
                "Docker command is required for production acceptance verification",
                "Assert-LastExitCode",
                "deploy.ps1",
                "Join-Path $PSScriptRoot \"deploy.ps1\"",
                "Join-Path $PSScriptRoot \"collect-deployment-evidence.ps1\"",
                "ReadinessTimeoutSeconds = $ReadinessTimeoutSeconds",
                "Verify = $true",
                "verify-deployment.ps1",
                "collect-deployment-evidence.ps1",
                "deployment-summary.ps1",
                "EvidencePathFile $evidencePathFile",
                "Get-Content -LiteralPath $evidencePathFile",
                "FrontendDir",
                "$env:VITE_API_BASE_URL = $BaseUrl",
                "$env:VITE_API_PREFIX = \"/api\"",
                "$env:VITE_ENABLE_MOCKS = \"false\"",
                "Resolve-NpmCommand",
                "& $npmCommand run test:e2e:backend",
                "frontend-smoke.txt",
                "FrontendSmokeStatus \"PASSED\"",
                "FrontendSmokeStatus \"FAILED\"",
                "FrontendSmokeStatus \"SKIPPED\"",
                "Frontend production backend smoke",
                "Production acceptance verification passed"
        );
        assertThat(productionAcceptance).doesNotContain("collect-deployment-evidence.ps1\" `\n    -EnvFile $envPath `\n    -BaseUrl $BaseUrl `\n    -Origin $Origin `\n    -OutputDir $evidencePath `\n    -EvidencePathFile $evidencePathFile `\n    -SkipMain");
        assertThat(verifyFrontend).contains(
                "FrontendDir = \"../.worktrees/Web_User\"",
                "SPRING_CONFIG_ADDITIONAL_LOCATION",
                "SERVER_PORT",
                "Assert-PortFree",
                "target/frontend-integration",
                "RedirectStandardOutput",
                "RedirectStandardError",
                "Stop-ProcessTree",
                "pgrep",
                "Write-LogTail",
                "Frontend integration verification failed. Backend smoke logs follow.",
                "-Dspring-boot.run.fork=false",
                "Resolve-MavenWrapper",
                "Resolve-MavenArguments",
                "Resolve-NpmCommand",
                "Test-IsWindows",
                "chmod +x $candidate",
                "D:\\tmp\\maven-aliyun-settings.xml",
                "return @(\"-s\", $MavenSettings, $Command)",
                "$startArgs.WindowStyle = \"Hidden\"",
                "/api/health",
                "VITE_API_BASE_URL",
                "VITE_ENABLE_MOCKS",
                "$previousViteApiBaseUrl",
                "$env:VITE_API_BASE_URL = $previousViteApiBaseUrl",
                "& $npmCommand run test:e2e:backend",
                "Assert-LastExitCode",
                "Web_User real-backend smoke",
                "Frontend integration verification passed"
        );
        assertThat(verifyLocal).contains(
                "FrontendDir = \"../.worktrees/Web_User\"",
                "Running local acceptance verification without Docker",
                "Resolve-MavenWrapper",
                "Resolve-MavenArguments",
                "Resolve-NpmCommand",
                "chmod +x $candidate",
                "D:\\tmp\\maven-aliyun-settings.xml",
                "return @(\"-s\", $MavenSettings, $Command)",
                "& $mavenWrapper @mavenTestArguments",
                "$global:LASTEXITCODE = 0",
                "& $npmCommand run test",
                "& $npmCommand run build",
                "& $npmCommand run test:e2e",
                "$frontendIntegrationScript = Join-Path $backendDir \"scripts/verify-frontend-integration.ps1\"",
                "[string]::IsNullOrWhiteSpace($MavenSettings)",
                "git diff --check",
                "Docker Compose production deployment still requires a Docker-enabled environment"
        );
    }

    @Test
    void deploymentGuideDocumentsStartupHealthAndVerification() throws IOException {
        String guide = read("BACKEND_DEPLOYMENT.md");

        assertThat(guide).contains(
                "docker compose up -d --build",
                "curl http://localhost:8080/api/health",
                "\"code\": 200",
                "\"database\": \"UP\"",
                "CORS_ALLOWED_ORIGINS",
                "Flyway",
                "mysqldump",
                "scripts/deploy.ps1",
                "scripts/backup.ps1",
                "scripts/restore.ps1",
                "scripts/validate-env.ps1",
                "scripts/verify-deployment.ps1",
                "scripts/deployment-summary.ps1",
                "scripts/collect-deployment-evidence.ps1",
                "scripts/verify-production-acceptance.ps1",
                "scripts/verify-frontend-integration.ps1",
                "scripts/verify-local-acceptance.ps1",
                "docker compose logs -f backend",
                ".\\scripts\\validate-env.ps1 -EnvFile .\\.env",
                ".\\scripts\\deploy.ps1 -EnvFile .\\.env -Verify -BaseUrl http://localhost:8080 -Origin http://localhost:5173",
                ".\\scripts\\verify-deployment.ps1 -BaseUrl http://localhost:8080 -Origin http://localhost:5173",
                ".\\scripts\\collect-deployment-evidence.ps1 -EnvFile .\\.env -BaseUrl http://localhost:8080 -Origin http://localhost:5173",
                ".\\scripts\\verify-production-acceptance.ps1 -EnvFile .\\.env -BaseUrl http://localhost:8080 -Origin http://localhost:5173 -FrontendDir ..\\.worktrees\\Web_User",
                ".\\scripts\\verify-frontend-integration.ps1 -FrontendDir ..\\.worktrees\\Web_User",
                ".\\scripts\\verify-local-acceptance.ps1 -FrontendDir ..\\.worktrees\\Web_User",
                ".\\mvnw.cmd -s D:\\tmp\\maven-aliyun-settings.xml test",
                "git diff --check",
                "docker compose config"
        );
        assertThat(guide).contains(
                "SPEC_DEPLOYMENT_ACCEPTANCE.md",
                "BACKEND_DEPLOYMENT_TRACEABILITY.md"
        );
    }

    @Test
    void githubActionsWorkflowRunsBackendDeploymentGates() throws IOException {
        String workflow = read("../.github/workflows/backend-ci.yml");
        String integrationWorkflow = read("../.github/workflows/frontend-integration-ci.yml");

        assertThat(workflow).contains(
                "Backend CI",
                "actions/setup-java@v4",
                "java-version: \"17\"",
                "Web_User",
                "./mvnw -B test",
                "./mvnw -B -DskipTests package",
                "git diff --check",
                "Prepare CI compose environment",
                "MYSQL_APP_PASSWORD=ci-app-password-32-characters",
                "JWT_SECRET=ci-jwt-secret-32-characters-minimum",
                "docker compose config",
                "docker build -t resource-sharing-forum-backend:ci .",
                "actions/upload-artifact@v4",
                "backend-deployment-handoff",
                "backend/Dockerfile",
                "backend/docker-compose.yml",
                "backend/pom.xml",
                "backend/.env.example",
                "backend/src/main/resources/application-prod.yml",
                "backend/src/main/resources/db/migration/",
                "backend/scripts/",
                "backend/target/*.jar",
                "backend/BACKEND_DEPLOYMENT.md",
                "backend/SPEC_DEPLOYMENT_ACCEPTANCE.md",
                "backend/API_CONTRACT.md",
                "if-no-files-found: error",
                "include-hidden-files: true"
        );
        assertThat(workflow).doesNotContain("backend/.env\n");
        assertThat(integrationWorkflow).contains(
                "Frontend Integration CI",
                "Check out web_user branch",
                "ref: Web_User",
                "Package backend smoke jar",
                "./mvnw -B -DskipTests package",
                "java -jar target/forum-backend-0.1.0-SNAPSHOT.jar",
                "SPRING_CONFIG_ADDITIONAL_LOCATION",
                "SERVER_PORT: \"18080\"",
                "http://127.0.0.1:18080/api/health",
                "VITE_API_BASE_URL: http://127.0.0.1:18080",
                "VITE_ENABLE_MOCKS: \"false\"",
                "npm run test:e2e:backend"
        );
    }

    @Test
    void traceabilityChecklistMapsDeploymentRequirementsToEvidence() throws IOException {
        String traceability = read("BACKEND_DEPLOYMENT_TRACEABILITY.md");

        assertThat(traceability).contains(
                "API contract remains unchanged",
                "Facade boundary remains in place",
                "Database design is deployed by migrations",
                "Frontend integration CORS is explicit",
                "Backend container deployment",
                "Production secrets are not committed",
                "Notification event boundary follows spec layering",
                "Local acceptance verification",
                "Structured logs include traceId, userId, and exception severity",
                "Sensitive auth endpoints are rate limited",
                "Security headers are applied",
                "Deploy, backup, restore, environment validation, production acceptance, evidence collection, and smoke verification scripts are available",
                "Spec deployment acceptance is mapped to evidence",
                "SPEC_DEPLOYMENT_ACCEPTANCE.md",
                "Requires a Docker-enabled environment"
        );
    }

    @Test
    void specHandoffDocumentsExistForApiFrontendAndSchema() throws IOException {
        assertThat(read("API_CONTRACT.md")).contains(
                "Response Wrapper",
                "\"code\": 200",
                "Pagination",
                "/api/v1",
                "Do not consume `storage_path`"
        );
        assertThat(read("FRONTEND_INTEGRATION_GUIDE.md")).contains(
                "CORS_ALLOWED_ORIGINS",
                "Authorization: Bearer <token>",
                "VITE_API_BASE_URL",
                "VITE_ENABLE_MOCKS=false",
                "npm run test:e2e:backend",
                "Disable MSW/mock registration",
                "verify-deployment.ps1 -BaseUrl http://localhost:8080 -Origin http://localhost:5173",
                "Never derive or display real server storage paths"
        );
        assertThat(read("SCHEMA.md")).contains(
                "MySQL 8.x",
                "utf8mb4",
                "notification_event",
                "system_notice",
                "Flyway"
        );
        assertThat(read("SPEC_DEPLOYMENT_ACCEPTANCE.md")).contains(
                "规范.docx",
                "详细设计说明书.pdf",
                "资源分享论坛——UML建模文档.pdf",
                "资源分享论坛——编码规范文档.pdf",
                "资源分享论坛——数据库设计说明书.pdf",
                "Maven + JDK 17 + Docker",
                "MySQL 8.x",
                "35 core tables",
                "Controller and public API contract remain compatible",
                "Security guardrails are deployment-ready",
                "Backup, restore, environment validation, production acceptance, evidence collection, and smoke verification are reusable",
                "verify-production-acceptance.ps1",
                "Frontend `Web_User` can switch from MSW to backend",
                "External Deployment Gate",
                "data.database=\"UP\""
        );
    }

    @Test
    void flywaySchemaKeepsDatabaseDesignCoreTableCoverage() throws IOException {
        String schema = read("src/main/resources/db/migration/V1__create_v2_schema.sql");

        long createTableCount = Pattern.compile("(?m)^CREATE TABLE IF NOT EXISTS ").matcher(schema).results().count();

        assertThat(createTableCount).isEqualTo(35);
        assertThat(schema).contains(
                "CREATE TABLE IF NOT EXISTS user_account",
                "CREATE TABLE IF NOT EXISTS resource_info",
                "CREATE TABLE IF NOT EXISTS file_attachment",
                "CREATE TABLE IF NOT EXISTS download_record",
                "CREATE TABLE IF NOT EXISTS user_interaction",
                "CREATE TABLE IF NOT EXISTS comment_info",
                "CREATE TABLE IF NOT EXISTS request_post",
                "CREATE TABLE IF NOT EXISTS request_reply",
                "CREATE TABLE IF NOT EXISTS point_flow",
                "CREATE TABLE IF NOT EXISTS report_complaint",
                "CREATE TABLE IF NOT EXISTS appeal_record",
                "CREATE TABLE IF NOT EXISTS notification_event",
                "CREATE TABLE IF NOT EXISTS system_notice",
                "CREATE TABLE IF NOT EXISTS admin_operation_log",
                "CREATE TABLE IF NOT EXISTS system_config",
                "FULLTEXT KEY ft_resource_search",
                "CONSTRAINT ck_download_status CHECK",
                "CONSTRAINT ck_event_status CHECK",
                "storage_path VARCHAR(700) NOT NULL"
        );
    }

    @Test
    void envFilesAreIgnoredAndExampleDocumentsRequiredInputs() throws IOException {
        String gitignore = read("../.gitignore");
        String envExample = read(".env.example");
        String dockerignore = read(".dockerignore");

        assertThat(gitignore).contains("/backend/.env");
        assertThat(envExample).contains(
                "MYSQL_ROOT_PASSWORD=",
                "MYSQL_APP_PASSWORD=",
                "CORS_ALLOWED_ORIGINS=",
                "JWT_SECRET=",
                "BACKEND_PORT=8080",
                "UPLOAD_MAX_FILE_SIZE_MB=100",
                "UPLOAD_MAX_FILES_PER_RESOURCE=5",
                "UPLOAD_MAX_FILE_SIZE=100MB",
                "UPLOAD_MAX_REQUEST_SIZE=500MB",
                "DB_POOL_MIN_IDLE=10",
                "DB_POOL_MAX_SIZE=50",
                "AUTH_RATE_LIMIT_WINDOW_SECONDS=60",
                "AUTH_RATE_LIMIT_MAX_REQUESTS=30"
        );
        assertThat(dockerignore).contains(
                "target/",
                "uploads/",
                ".env"
        );
        assertThat(gitignore).contains("/backend/deployment-evidence/");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
