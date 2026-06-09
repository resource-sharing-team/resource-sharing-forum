package com.resourcesharing.forum;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentScriptsTest {
    @TempDir
    private Path tempDir;

    @Test
    void validateEnvRejectsExamplePlaceholderSecrets() throws Exception {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, """
                MYSQL_ROOT_PASSWORD=change-this-root-password
                MYSQL_APP_USER=forum_app
                MYSQL_APP_PASSWORD=change-this-app-password
                MYSQL_DATABASE=resource_sharing_forum
                CORS_ALLOWED_ORIGINS=http://localhost:5173
                JWT_SECRET=replace-with-at-least-32-random-characters
                """);

        ProcessResult result = runValidateEnv(env);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("must be replaced before deployment");
    }

    @Test
    void validateEnvAcceptsStrongDeploymentValues() throws Exception {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, """
                MYSQL_ROOT_PASSWORD=strong-root-password-2026
                MYSQL_APP_USER=forum_app
                MYSQL_APP_PASSWORD=strong-app-password-2026
                MYSQL_DATABASE=resource_sharing_forum
                CORS_ALLOWED_ORIGINS=http://localhost:5173,https://forum.example.com
                JWT_SECRET=strong-jwt-secret-32-characters-minimum
                """);

        ProcessResult result = runValidateEnv(env);

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("Environment validation passed");
    }

    @Test
    void backupRequiresEnvFileBeforeRunningDockerCommands() throws Exception {
        Path missingEnv = tempDir.resolve("missing.env");

        ProcessResult result = runScript("scripts/backup.ps1", "-EnvFile", missingEnv.toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Environment file not found");
    }

    @Test
    void deployRequiresEnvFileBeforeRunningDockerCompose() throws Exception {
        Path missingEnv = tempDir.resolve("missing.env");

        ProcessResult result = runScript("scripts/deploy.ps1", "-EnvFile", missingEnv.toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Environment file not found");
    }

    @Test
    void deployChecksDockerAvailabilityAfterEnvironmentValidation() throws Exception {
        Path env = strongEnvFile();

        ProcessResult result = runScriptWithoutDocker("scripts/deploy.ps1", "-EnvFile", env.toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Environment validation passed");
        assertThat(result.output()).contains("Docker command is required for deployment");
    }

    @Test
    void deploymentScriptsResolveRelativeEnvFileFromBackendRoot() throws Exception {
        Path callerDir = Files.createDirectory(tempDir.resolve("caller"));
        String callerOnlyEnvName = "caller-only-env-" + System.nanoTime() + ".env";
        Files.writeString(callerDir.resolve(callerOnlyEnvName), """
                MYSQL_ROOT_PASSWORD=strong-root-password-2026
                MYSQL_APP_USER=forum_app
                MYSQL_APP_PASSWORD=strong-app-password-2026
                MYSQL_DATABASE=resource_sharing_forum
                CORS_ALLOWED_ORIGINS=http://localhost:5173
                JWT_SECRET=strong-jwt-secret-32-characters-minimum
                """);

        ProcessResult result = runScriptFrom(
                callerDir,
                scriptPath("scripts/deploy.ps1"),
                "-EnvFile",
                callerOnlyEnvName
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Environment file not found");
        assertThat(result.output().replaceAll("\\s+", "")).contains(callerOnlyEnvName);
        assertThat(result.output()).doesNotContain("Environment validation passed");
    }

    @Test
    void restoreRequiresEnvFileBeforeRunningDockerCommands() throws Exception {
        Path missingEnv = tempDir.resolve("missing.env");
        Path databaseBackup = tempDir.resolve("backup.sql");
        Files.writeString(databaseBackup, "SELECT 1;");

        ProcessResult result = runScript(
                "scripts/restore.ps1",
                "-DatabaseBackup",
                databaseBackup.toString(),
                "-EnvFile",
                missingEnv.toString()
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Environment file not found");
    }

    @Test
    void restoreValidatesUploadBackupPathBeforeRunningDockerVolumeRestore() throws Exception {
        Path env = strongEnvFile();
        Path databaseBackup = tempDir.resolve("backup.sql");
        Path missingUploadBackup = tempDir.resolve("missing-upload.tar");
        Files.writeString(databaseBackup, "SELECT 1;");

        ProcessResult result = runScript(
                "scripts/restore.ps1",
                "-DatabaseBackup",
                databaseBackup.toString(),
                "-UploadBackup",
                missingUploadBackup.toString(),
                "-EnvFile",
                env.toString()
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Resolve-Path");
        assertThat(result.output()).contains("missing-upload.tar");
    }

    @Test
    void backupImportsEnvFileOverExistingProcessEnvironment() throws Exception {
        Path env = tempDir.resolve("backup.env");
        Files.writeString(env, """
                MYSQL_ROOT_PASSWORD=env-root-password-2026
                MYSQL_APP_USER=forum_app
                MYSQL_APP_PASSWORD=strong-app-password-2026
                MYSQL_DATABASE=env_database
                CORS_ALLOWED_ORIGINS=http://localhost:5173
                JWT_SECRET=strong-jwt-secret-32-characters-minimum
                """);
        Path outputDir = tempDir.resolve("backups");
        Path capture = tempDir.resolve("docker-capture.txt");
        Path fakeDockerDir = fakeDockerDirectory(capture);

        ProcessResult result = runScriptWithEnvironment(
                fakeDockerDir.toString(),
                null,
                Map.of(
                        "MYSQL_DATABASE", "stale_database",
                        "MYSQL_ROOT_PASSWORD", "stale-root-password",
                        "DOCKER_CAPTURE", capture.toString()
                ),
                "scripts/backup.ps1",
                "-EnvFile",
                env.toString(),
                "-OutputDir",
                outputDir.toString()
        );

        assertThat(result.exitCode()).isZero();
        assertThat(Files.readString(capture))
                .contains("MYSQL_DATABASE=env_database")
                .contains("MYSQL_ROOT_PASSWORD=env-root-password-2026")
                .doesNotContain("MYSQL_DATABASE=stale_database")
                .doesNotContain("MYSQL_ROOT_PASSWORD=stale-root-password");
    }

    @Test
    void backupFailsWhenDockerCommandReturnsNonZero() throws Exception {
        Path env = strongEnvFile();
        Path outputDir = tempDir.resolve("backups");
        Path failingDockerDir = fakeFailingDockerDirectory();

        ProcessResult result = runScriptWithEnvironment(
                failingDockerDir.toString(),
                null,
                Map.of(),
                "scripts/backup.ps1",
                "-EnvFile",
                env.toString(),
                "-OutputDir",
                outputDir.toString()
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Docker Compose availability check failed with exit code 42");
    }

    @Test
    void backupFailsWhenDatabaseDumpCommandReturnsNonZero() throws Exception {
        Path env = strongEnvFile();
        Path outputDir = tempDir.resolve("backups");
        Path failingBackupDockerDir = fakeFailingBackupDockerDirectory();

        ProcessResult result = runScriptWithEnvironment(
                failingBackupDockerDir.toString(),
                null,
                Map.of(),
                "scripts/backup.ps1",
                "-EnvFile",
                env.toString(),
                "-OutputDir",
                outputDir.toString()
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Database backup failed with exit code 43");
    }

    @Test
    void backupChecksDockerAvailabilityAfterEnvironmentValidation() throws Exception {
        Path env = strongEnvFile();

        ProcessResult result = runScriptWithoutDocker("scripts/backup.ps1", "-EnvFile", env.toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Environment validation passed");
        assertThat(result.output()).contains("Docker command is required for backup");
    }

    @Test
    void restoreChecksDockerAvailabilityAfterEnvironmentAndBackupPathValidation() throws Exception {
        Path env = strongEnvFile();
        Path databaseBackup = tempDir.resolve("backup.sql");
        Files.writeString(databaseBackup, "SELECT 1;");

        ProcessResult result = runScriptWithoutDocker(
                "scripts/restore.ps1",
                "-DatabaseBackup",
                databaseBackup.toString(),
                "-EnvFile",
                env.toString()
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Environment validation passed");
        assertThat(result.output()).contains("Docker command is required for restore");
    }

    @Test
    void evidenceSummaryRecordsDatabaseUpAndSmokePass() throws Exception {
        Path evidenceDir = Files.createDirectory(tempDir.resolve("evidence"));
        Files.writeString(evidenceDir.resolve("api-health.json"), """
                {
                  "statusCode": 200,
                  "headers": {},
                  "body": "{\\"code\\":200,\\"message\\":\\"success\\",\\"data\\":{\\"status\\":\\"UP\\",\\"database\\":\\"UP\\"},\\"timestamp\\":\\"2026-06-06T00:00:00\\"}"
                }
                """);
        Files.writeString(evidenceDir.resolve("verify-deployment.txt"), "Deployment verification passed.");

        String command = ". .\\scripts\\collect-deployment-evidence.ps1 -SkipMain; "
                + "Save-AcceptanceSummary '" + evidenceDir + "' 'http://localhost:8080' 'http://localhost:5173'";

        ProcessResult result = runPowerShellCommand(command);

        assertThat(result.exitCode()).isZero();
        assertThat(Files.readString(evidenceDir.resolve("deployment-acceptance-summary.json")))
                .contains("\"passed\"")
                .contains("true")
                .contains("\"healthStatus\"")
                .contains("\"healthDatabase\"")
                .contains("\"UP\"")
                .contains("\"smokePassed\"");
    }

    @Test
    void deploymentVerificationReadsHeadersAcrossPowerShellResponseTypes() throws Exception {
        String command = ". .\\scripts\\verify-deployment.ps1 -SkipMain; "
                + "$headers = New-Object System.Net.WebHeaderCollection; "
                + "$headers.Add('x-frame-options', 'DENY'); "
                + "$response = [pscustomobject]@{ Headers = $headers }; "
                + "Assert-HeaderValue $response 'X-Frame-Options' 'DENY' 'legacy response'";

        ProcessResult result = runPowerShellCommand(command);

        assertThat(result.exitCode()).isZero();
    }

    @Test
    void deploymentVerificationFailsClearlyWhenExpectedHeaderIsMissing() throws Exception {
        String command = ". .\\scripts\\verify-deployment.ps1 -SkipMain; "
                + "$response = [pscustomobject]@{ Headers = @{} }; "
                + "Assert-Header $response 'X-Trace-Id' 'health'";

        ProcessResult result = runPowerShellCommand(command);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("health missing response header X-Trace-Id");
    }

    @Test
    void evidenceSummaryRecordsFrontendSmokePass() throws Exception {
        Path evidenceDir = healthyEvidenceDir("frontend-pass-evidence");
        String command = ". .\\scripts\\deployment-summary.ps1; "
                + "Save-AcceptanceSummary '" + evidenceDir + "' 'http://localhost:8080' 'http://localhost:5173' "
                + "-FrontendSmokeStatus 'PASSED' -FrontendSmokeLog 'frontend-smoke.txt'";

        ProcessResult result = runPowerShellCommand(command);

        assertThat(result.exitCode()).isZero();
        assertThat(Files.readString(evidenceDir.resolve("deployment-acceptance-summary.json")))
                .contains("\"passed\"")
                .contains("true")
                .contains("\"frontendSmokeStatus\"")
                .contains("\"PASSED\"")
                .contains("\"frontendSmokePassed\"")
                .contains("\"frontendSmokeLog\"")
                .contains("\"frontend-smoke.txt\"");
    }

    @Test
    void evidenceSummaryFailsWhenRequiredFrontendSmokeFails() throws Exception {
        Path evidenceDir = healthyEvidenceDir("frontend-fail-evidence");
        String command = ". .\\scripts\\deployment-summary.ps1; "
                + "Save-AcceptanceSummary '" + evidenceDir + "' 'http://localhost:8080' 'http://localhost:5173' "
                + "-FrontendSmokeStatus 'FAILED' -FrontendSmokeLog 'frontend-smoke.txt' -FrontendSmokeMessage 'playwright failed'";

        ProcessResult result = runPowerShellCommand(command);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Deployment acceptance summary failed");
        assertThat(Files.readString(evidenceDir.resolve("deployment-acceptance-summary.json")))
                .contains("\"passed\"")
                .contains("false")
                .contains("\"frontendSmokeStatus\"")
                .contains("\"FAILED\"")
                .contains("playwright failed");
    }

    @Test
    void deployReadinessWaitFailsClearlyWhenDatabaseNeverBecomesUp() throws Exception {
        String command = ". .\\scripts\\deploy.ps1 -SkipMain; "
                + "Wait-BackendReadiness 'http://127.0.0.1:9' 1";

        ProcessResult result = runPowerShellCommand(command);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Backend did not become ready with database=UP within 1 seconds");
    }

    @Test
    void frontendIntegrationVerificationRequiresExistingFrontendWorktree() throws Exception {
        Path missingFrontend = tempDir.resolve("missing-web-user");

        ProcessResult result = runScript(
                "scripts/verify-frontend-integration.ps1",
                "-FrontendDir",
                missingFrontend.toString()
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Path not found");
        assertThat(result.output()).contains("missing-web-user");
    }

    @Test
    void productionAcceptanceRequiresEnvFileBeforeRunningDockerCommands() throws Exception {
        Path missingEnv = tempDir.resolve("missing.env");

        ProcessResult result = runScript(
                "scripts/verify-production-acceptance.ps1",
                "-EnvFile",
                missingEnv.toString()
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Environment file not found");
    }

    @Test
    void productionAcceptanceSkipBuildChecksDockerAvailabilityAfterEnvironmentValidation() throws Exception {
        Path env = strongEnvFile();

        ProcessResult result = runScriptWithoutDocker(
                "scripts/verify-production-acceptance.ps1",
                "-EnvFile",
                env.toString(),
                "-SkipBuild"
        );

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Environment validation passed");
        assertThat(result.output()).contains("Docker command is required for production acceptance verification");
    }

    private static ProcessResult runValidateEnv(Path env) throws IOException, InterruptedException {
        return runScript(
                "scripts/validate-env.ps1",
                "-EnvFile",
                env.toString()
        );
    }

    private Path strongEnvFile() throws IOException {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, """
                MYSQL_ROOT_PASSWORD=strong-root-password-2026
                MYSQL_APP_USER=forum_app
                MYSQL_APP_PASSWORD=strong-app-password-2026
                MYSQL_DATABASE=resource_sharing_forum
                CORS_ALLOWED_ORIGINS=http://localhost:5173,https://forum.example.com
                JWT_SECRET=strong-jwt-secret-32-characters-minimum
                """);
        return env;
    }

    private Path healthyEvidenceDir(String name) throws IOException {
        Path evidenceDir = Files.createDirectory(tempDir.resolve(name));
        Files.writeString(evidenceDir.resolve("api-health.json"), """
                {
                  "statusCode": 200,
                  "headers": {},
                  "body": "{\\"code\\":200,\\"message\\":\\"success\\",\\"data\\":{\\"status\\":\\"UP\\",\\"database\\":\\"UP\\"},\\"timestamp\\":\\"2026-06-06T00:00:00\\"}"
                }
                """);
        Files.writeString(evidenceDir.resolve("verify-deployment.txt"), "Deployment verification passed.");
        return evidenceDir;
    }

    private static ProcessResult runScript(String... command) throws IOException, InterruptedException {
        return runScriptWithEnvironment(null, null, Map.of(), command);
    }

    private static ProcessResult runScriptFrom(Path workingDirectory, String... command)
            throws IOException, InterruptedException {
        return runScriptWithEnvironment(null, workingDirectory, Map.of(), command);
    }

    private static ProcessResult runScriptWithoutDocker(String... command) throws IOException, InterruptedException {
        Path noDockerPath = Files.createTempDirectory("no-docker-path");
        return runScriptWithEnvironment(noDockerPath.toString(), null, Map.of(), command);
    }

    private static ProcessResult runPowerShellCommand(String command) throws IOException, InterruptedException {
        String[] fullCommand = new String[] {
                powerShellExecutable(),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                command
        };
        Process process = new ProcessBuilder(fullCommand)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("PowerShell command did not finish");
        }
        return new ProcessResult(process.exitValue(), new String(process.getInputStream().readAllBytes()));
    }

    private Path fakeDockerDirectory(Path capture) throws IOException {
        Path fakeDockerDir = Files.createDirectory(tempDir.resolve("fake-docker"));
        Files.writeString(fakeDockerDir.resolve("docker.cmd"), """
                @echo off
                echo %*>> "%DOCKER_CAPTURE%"
                echo MYSQL_DATABASE=%MYSQL_DATABASE%>> "%DOCKER_CAPTURE%"
                echo MYSQL_ROOT_PASSWORD=%MYSQL_ROOT_PASSWORD%>> "%DOCKER_CAPTURE%"
                echo FAKE_DOCKER_OUTPUT
                exit /b 0
                """);
        writeExecutable(fakeDockerDir.resolve("docker"), """
                #!/bin/sh
                echo "$*" >> "$DOCKER_CAPTURE"
                echo "MYSQL_DATABASE=$MYSQL_DATABASE" >> "$DOCKER_CAPTURE"
                echo "MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD" >> "$DOCKER_CAPTURE"
                echo FAKE_DOCKER_OUTPUT
                exit 0
                """);
        return fakeDockerDir;
    }

    private Path fakeFailingDockerDirectory() throws IOException {
        Path fakeDockerDir = Files.createDirectory(tempDir.resolve("fake-failing-docker"));
        Files.writeString(fakeDockerDir.resolve("docker.cmd"), """
                @echo off
                echo FAKE_DOCKER_FAILURE
                exit /b 42
                """);
        writeExecutable(fakeDockerDir.resolve("docker"), """
                #!/bin/sh
                echo FAKE_DOCKER_FAILURE
                exit 42
                """);
        return fakeDockerDir;
    }

    private Path fakeFailingBackupDockerDirectory() throws IOException {
        Path fakeDockerDir = Files.createDirectory(tempDir.resolve("fake-failing-backup-docker"));
        Files.writeString(fakeDockerDir.resolve("docker.cmd"), """
                @echo off
                if "%1"=="compose" if "%2"=="version" (
                  echo Docker Compose version v2.0.0
                  exit /b 0
                )
                echo FAKE_DATABASE_BACKUP_FAILURE
                exit /b 43
                """);
        writeExecutable(fakeDockerDir.resolve("docker"), """
                #!/bin/sh
                if [ "$1" = "compose" ] && [ "$2" = "version" ]; then
                  echo Docker Compose version v2.0.0
                  exit 0
                fi
                echo FAKE_DATABASE_BACKUP_FAILURE
                exit 43
                """);
        return fakeDockerDir;
    }

    private static ProcessResult runScriptWithEnvironment(
            String pathOverride,
            Path workingDirectory,
            Map<String, String> environment,
            String... command)
            throws IOException, InterruptedException {
        String[] baseCommand = new String[] {
                powerShellExecutable(),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File"
        };
        String[] fullCommand = new String[baseCommand.length + command.length];
        System.arraycopy(baseCommand, 0, fullCommand, 0, baseCommand.length);
        System.arraycopy(command, 0, fullCommand, baseCommand.length, command.length);

        ProcessBuilder processBuilder = new ProcessBuilder(fullCommand)
                .redirectErrorStream(true);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }
        if (pathOverride != null) {
            processBuilder.environment().put("Path", pathOverride);
            processBuilder.environment().put("PATH", pathOverride);
        }
        processBuilder.environment().putAll(environment);
        Process process = processBuilder.start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(command[0] + " did not finish");
        }
        return new ProcessResult(process.exitValue(), new String(process.getInputStream().readAllBytes()));
    }

    private static String powerShellExecutable() {
        String fromPath = findOnPath(isWindows() ? "pwsh.exe" : "pwsh");
        if (fromPath != null) {
            return fromPath;
        }
        fromPath = findOnPath(isWindows() ? "powershell.exe" : "powershell");
        if (fromPath != null) {
            return fromPath;
        }
        if (isWindows()) {
            return Path.of(
                    System.getenv().getOrDefault("SystemRoot", "C:\\Windows"),
                    "System32",
                    "WindowsPowerShell",
                    "v1.0",
                    "powershell.exe"
            ).toString();
        }
        throw new IllegalStateException("PowerShell executable not found on PATH");
    }

    private static String scriptPath(String relativePath) {
        return Path.of(relativePath).toAbsolutePath().toString();
    }

    private static void writeExecutable(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
        path.toFile().setExecutable(true);
    }

    private static String findOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            path = System.getenv("Path");
        }
        if (path == null || path.isBlank()) {
            return null;
        }
        for (String entry : path.split(java.io.File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            Path candidate = Path.of(entry, executable);
            if (Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
