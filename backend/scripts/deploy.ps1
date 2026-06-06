param(
    [string]$EnvFile = "./.env",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Origin = "http://localhost:5173",
    [int]$ReadinessTimeoutSeconds = 120,
    [switch]$Verify,
    [switch]$SkipMain
)

$ErrorActionPreference = "Stop"
$BackendRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path

function Resolve-BackendPath {
    param([string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return Join-Path $BackendRoot $Path
}

function Assert-DockerComposeAvailable {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker command is required for deployment"
    }

    docker compose version | Out-Null
    Assert-LastExitCode "Docker Compose availability check"
}

function Assert-LastExitCode {
    param([string]$Label)

    if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) {
        throw "$Label failed with exit code $LASTEXITCODE"
    }
}

function Wait-BackendReadiness {
    param(
        [string]$BaseUrl,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri "$BaseUrl/api/health" -UseBasicParsing -TimeoutSec 5
            $body = $response.Content | ConvertFrom-Json
            if ([int]$response.StatusCode -eq 200 -and $body.data.status -eq "UP" -and $body.data.database -eq "UP") {
                Write-Host "Backend readiness confirmed with database=UP."
                return
            }
        } catch {
            Start-Sleep -Seconds 2
            continue
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    throw "Backend did not become ready with database=UP within $TimeoutSeconds seconds"
}

$envPath = Resolve-BackendPath $EnvFile

if ($SkipMain) {
    return
}

& (Join-Path $PSScriptRoot "validate-env.ps1") -EnvFile $envPath
Assert-DockerComposeAvailable

Write-Host "Starting backend deployment with Docker Compose..."
Push-Location $BackendRoot
try {
    docker compose --env-file $envPath up -d --build
    Assert-LastExitCode "Docker Compose deployment"
} finally {
    Pop-Location
}

if ($Verify) {
    Write-Host "Waiting for backend readiness before smoke verification..."
    Wait-BackendReadiness $BaseUrl $ReadinessTimeoutSeconds
    & (Join-Path $PSScriptRoot "verify-deployment.ps1") -BaseUrl $BaseUrl -Origin $Origin
}

Write-Host "Backend deployment command completed."
