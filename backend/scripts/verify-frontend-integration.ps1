param(
    [string]$FrontendDir = "../.worktrees/Web_User",
    [string]$MavenSettings = "",
    [int]$BackendPort = 18080,
    [int]$StartupTimeoutSeconds = 90
)

$ErrorActionPreference = "Stop"

function Resolve-RepoPath {
    param([string]$Path)

    $candidate = $Path
    if (-not [System.IO.Path]::IsPathRooted($candidate)) {
        $candidate = Join-Path (Get-Location) $candidate
    }
    if (-not (Test-Path -LiteralPath $candidate)) {
        throw "Path not found: $Path"
    }
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return (Resolve-Path -LiteralPath $candidate).Path
    }
    return (Resolve-Path -LiteralPath $candidate).Path
}

function Wait-BackendReady {
    param(
        [string]$BaseUrl,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri "$BaseUrl/api/health" -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) {
                Write-Host "Backend smoke server is ready at $BaseUrl"
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)

    throw "Backend smoke server did not become ready at $BaseUrl within $TimeoutSeconds seconds"
}

function Assert-PortFree {
    param([int]$Port)

    $listener = $null
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Parse("127.0.0.1"), $Port)
        $listener.Start()
    } catch {
        throw "Backend smoke port $Port is already in use or unavailable"
    } finally {
        if ($listener) {
            $listener.Stop()
        }
    }
}

function Stop-ProcessTree {
    param([int]$ProcessId)

    if (Test-IsWindows) {
        $children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue
        foreach ($child in $children) {
            Stop-ProcessTree -ProcessId $child.ProcessId
        }
    } else {
        $pgrep = Get-Command pgrep -ErrorAction SilentlyContinue
        if ($pgrep) {
            $children = & $pgrep.Source -P $ProcessId 2>$null
            foreach ($child in $children) {
                if ($child -match "^\d+$") {
                    Stop-ProcessTree -ProcessId ([int]$child)
                }
            }
        }
    }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

function Write-LogTail {
    param(
        [string]$Path,
        [string]$Label
    )

    Write-Host "$Label log: $Path"
    if (Test-Path -LiteralPath $Path) {
        Get-Content -LiteralPath $Path -Tail 80 -ErrorAction SilentlyContinue
    }
}

function Assert-LastExitCode {
    param([string]$Label)

    if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) {
        throw "$Label failed with exit code $LASTEXITCODE"
    }
}

function Test-IsWindows {
    return [System.IO.Path]::DirectorySeparatorChar -eq "\"
}

function Resolve-NpmCommand {
    $candidates = if (Test-IsWindows) { @("npm.cmd", "npm") } else { @("npm", "npm.cmd") }
    foreach ($candidate in $candidates) {
        $command = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($command) {
            return $command.Source
        }
    }
    throw "npm command is required for frontend smoke verification"
}

function Resolve-MavenWrapper {
    param([string]$BackendDir)

    $candidate = if (Test-IsWindows) { Join-Path $BackendDir "mvnw.cmd" } else { Join-Path $BackendDir "mvnw" }
    if (Test-Path -LiteralPath $candidate) {
        if (-not (Test-IsWindows)) {
            chmod +x $candidate
            Assert-LastExitCode "Maven wrapper chmod"
        }
        return $candidate
    }

    $fallback = if (Test-IsWindows) { Join-Path $BackendDir "mvnw" } else { Join-Path $BackendDir "mvnw.cmd" }
    if (Test-Path -LiteralPath $fallback) {
        return $fallback
    }

    throw "Maven wrapper not found in $BackendDir"
}

function Resolve-MavenArguments {
    param(
        [string]$MavenSettings,
        [string]$Command
    )

    if ([string]::IsNullOrWhiteSpace($MavenSettings) -and (Test-IsWindows)) {
        $defaultWindowsSettings = "D:\tmp\maven-aliyun-settings.xml"
        if (Test-Path -LiteralPath $defaultWindowsSettings) {
            $MavenSettings = $defaultWindowsSettings
        }
    }

    if ([string]::IsNullOrWhiteSpace($MavenSettings)) {
        return @($Command)
    }
    if (-not (Test-Path -LiteralPath $MavenSettings)) {
        throw "Maven settings file not found at $MavenSettings"
    }
    return @("-s", $MavenSettings, $Command)
}

$backendDir = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")
$frontendPath = Resolve-RepoPath $FrontendDir
$backendBaseUrl = "http://127.0.0.1:$BackendPort"
$testConfig = Join-Path $backendDir "src/test/resources/application.yml"
$npmCommand = Resolve-NpmCommand
$backendCommand = Resolve-MavenWrapper $backendDir

if (-not (Test-Path -LiteralPath (Join-Path $frontendPath "package.json"))) {
    throw "Frontend package.json not found at $frontendPath"
}
Assert-PortFree -Port $BackendPort

Write-Host "Starting backend smoke server without database on $backendBaseUrl..."
$logDir = Join-Path $backendDir "target/frontend-integration"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$stdoutLog = Join-Path $logDir "backend-smoke.out.log"
$stderrLog = Join-Path $logDir "backend-smoke.err.log"
$backendArguments = @(Resolve-MavenArguments $MavenSettings "spring-boot:run")
$previousMavenOpts = $env:MAVEN_OPTS
$previousAdditionalLocation = $env:SPRING_CONFIG_ADDITIONAL_LOCATION
$previousServerPort = $env:SERVER_PORT
$previousViteApiBaseUrl = $env:VITE_API_BASE_URL
$previousViteApiPrefix = $env:VITE_API_PREFIX
$previousViteEnableMocks = $env:VITE_ENABLE_MOCKS
$env:MAVEN_OPTS = "-Dspring-boot.run.fork=false"
$env:SPRING_CONFIG_ADDITIONAL_LOCATION = "file:$testConfig"
$env:SERVER_PORT = [string]$BackendPort

$backendProcess = $null
try {
    $startArgs = @{
        FilePath = $backendCommand
        WorkingDirectory = $backendDir
        ArgumentList = $backendArguments
        PassThru = $true
        RedirectStandardOutput = $stdoutLog
        RedirectStandardError = $stderrLog
    }
    if (Test-IsWindows) {
        $startArgs.WindowStyle = "Hidden"
    }
    $backendProcess = Start-Process @startArgs
    Wait-BackendReady -BaseUrl $backendBaseUrl -TimeoutSeconds $StartupTimeoutSeconds

    Write-Host "Running web_user real-backend e2e smoke..."
    $env:VITE_API_BASE_URL = $backendBaseUrl
    $env:VITE_API_PREFIX = "/api"
    $env:VITE_ENABLE_MOCKS = "false"
    Push-Location $frontendPath
    try {
        & $npmCommand run test:e2e:backend
        Assert-LastExitCode "Web_User real-backend smoke"
    } finally {
        Pop-Location
    }

    Write-Host "Frontend integration verification passed."
} catch {
    Write-Host "Frontend integration verification failed. Backend smoke logs follow."
    Write-LogTail -Path $stdoutLog -Label "Backend smoke stdout"
    Write-LogTail -Path $stderrLog -Label "Backend smoke stderr"
    throw
} finally {
    if ($backendProcess -and -not $backendProcess.HasExited) {
        Stop-ProcessTree -ProcessId $backendProcess.Id
        $backendProcess.WaitForExit(5000) | Out-Null
    }
    $env:MAVEN_OPTS = $previousMavenOpts
    $env:SPRING_CONFIG_ADDITIONAL_LOCATION = $previousAdditionalLocation
    $env:SERVER_PORT = $previousServerPort
    $env:VITE_API_BASE_URL = $previousViteApiBaseUrl
    $env:VITE_API_PREFIX = $previousViteApiPrefix
    $env:VITE_ENABLE_MOCKS = $previousViteEnableMocks
}
