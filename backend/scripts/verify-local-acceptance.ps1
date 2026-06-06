param(
    [string]$FrontendDir = "../.worktrees/Web_User",
    [string]$MavenSettings = "",
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$SkipIntegration
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
    return (Resolve-Path -LiteralPath $candidate).Path
}

function Invoke-Step {
    param(
        [string]$Label,
        [scriptblock]$Action
    )

    Write-Host ""
    Write-Host "==> $Label"
    $global:LASTEXITCODE = 0
    & $Action
    if ($LASTEXITCODE -ne 0) {
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
    throw "npm command is required for local acceptance verification"
}

function Resolve-MavenWrapper {
    param([string]$BackendDir)

    $candidate = if (Test-IsWindows) { Join-Path $BackendDir "mvnw.cmd" } else { Join-Path $BackendDir "mvnw" }
    if (Test-Path -LiteralPath $candidate) {
        if (-not (Test-IsWindows)) {
            chmod +x $candidate
            if ($LASTEXITCODE -ne 0) {
                throw "Maven wrapper chmod failed with exit code $LASTEXITCODE"
            }
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

$backendDir = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $backendDir "..")).Path
$frontendPath = Resolve-RepoPath $FrontendDir

if (-not $SkipFrontend -and -not (Test-Path -LiteralPath (Join-Path $frontendPath "package.json"))) {
    throw "Frontend package.json not found at $frontendPath"
}

Write-Host "Running local acceptance verification without Docker."
Write-Host "Backend:  $backendDir"
Write-Host "Frontend: $frontendPath"

if (-not $SkipBackend) {
    $mavenWrapper = Resolve-MavenWrapper $backendDir
    $mavenTestArguments = @(Resolve-MavenArguments $MavenSettings "test")
    Push-Location $backendDir
    try {
        Invoke-Step "Backend Maven tests" {
            & $mavenWrapper @mavenTestArguments
        }
    } finally {
        Pop-Location
    }
}

if (-not $SkipFrontend) {
    $npmCommand = Resolve-NpmCommand
    Push-Location $frontendPath
    try {
        Invoke-Step "Frontend unit tests" {
            & $npmCommand run test
        }
        Invoke-Step "Frontend production build" {
            & $npmCommand run build
        }
        Invoke-Step "Frontend default e2e tests" {
            & $npmCommand run test:e2e
        }
    } finally {
        Pop-Location
    }
}

if (-not $SkipIntegration) {
    Push-Location $backendDir
    try {
        Invoke-Step "Backend plus Web_User real-backend smoke" {
            $frontendIntegrationScript = Join-Path $backendDir "scripts/verify-frontend-integration.ps1"
            if ([string]::IsNullOrWhiteSpace($MavenSettings)) {
                & $frontendIntegrationScript -FrontendDir $frontendPath
            } else {
                & $frontendIntegrationScript -FrontendDir $frontendPath -MavenSettings $MavenSettings
            }
        }
    } finally {
        Pop-Location
    }
}

Push-Location $repoRoot
try {
    Invoke-Step "Backend worktree diff whitespace check" {
        git diff --check
    }
} finally {
    Pop-Location
}

Push-Location $frontendPath
try {
    Invoke-Step "Web_User worktree diff whitespace check" {
        git diff --check
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "Local acceptance verification passed."
Write-Host "Docker Compose production deployment still requires a Docker-enabled environment."
