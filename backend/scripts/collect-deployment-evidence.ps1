param(
    [string]$EnvFile = "./.env",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Origin = "http://localhost:5173",
    [string]$OutputDir = "./deployment-evidence",
    [string]$EvidencePathFile = "",
    [switch]$SkipMain
)

$ErrorActionPreference = "Stop"
$BackendRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "deployment-summary.ps1")

function Resolve-BackendPath {
    param([string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return Join-Path $BackendRoot $Path
}

function Assert-DockerComposeAvailable {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker command is required to collect deployment evidence"
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

function Save-CommandOutput {
    param(
        [string]$Label,
        [string]$Path,
        [scriptblock]$Command
    )

    Write-Host "Collecting $Label -> $Path"
    try {
        $global:LASTEXITCODE = 0
        & $Command *>&1 | Tee-Object -FilePath $Path
        Assert-LastExitCode $Label
    } catch {
        $_ | Out-File -FilePath $Path -Append
        throw
    }
}

function Save-WebResponse {
    param(
        [string]$Uri,
        [string]$Path
    )

    Write-Host "Collecting HTTP response $Uri -> $Path"
    try {
        $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 10
        $record = [ordered]@{
            statusCode = [int]$response.StatusCode
            headers = $response.Headers
            body = $response.Content
        }
        $record | ConvertTo-Json -Depth 8 | Out-File -FilePath $Path -Encoding UTF8
    } catch {
        $_ | Out-File -FilePath $Path -Encoding UTF8
        throw
    }
}

$envPath = Resolve-BackendPath $EnvFile
$outputRoot = Resolve-BackendPath $OutputDir
$evidencePathFilePath = if ([string]::IsNullOrWhiteSpace($EvidencePathFile)) { "" } else { Resolve-BackendPath $EvidencePathFile }

if ($SkipMain) {
    return
}

& (Join-Path $PSScriptRoot "validate-env.ps1") -EnvFile $envPath
Assert-DockerComposeAvailable

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$evidenceDir = Join-Path $outputRoot $stamp
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
if (-not [string]::IsNullOrWhiteSpace($evidencePathFilePath)) {
    $pathFileParent = Split-Path -Parent $evidencePathFilePath
    if (-not [string]::IsNullOrWhiteSpace($pathFileParent)) {
        New-Item -ItemType Directory -Force -Path $pathFileParent | Out-Null
    }
    $evidenceDir | Out-File -FilePath $evidencePathFilePath -Encoding UTF8
}

Save-CommandOutput "docker compose config" (Join-Path $evidenceDir "docker-compose-config.txt") {
    Push-Location $BackendRoot
    try {
        docker compose --env-file $envPath config
    } finally {
        Pop-Location
    }
}
Save-CommandOutput "docker compose ps" (Join-Path $evidenceDir "docker-compose-ps.txt") {
    Push-Location $BackendRoot
    try {
        docker compose --env-file $envPath ps
    } finally {
        Pop-Location
    }
}
Save-WebResponse "$BaseUrl/api/health" (Join-Path $evidenceDir "api-health.json")
Save-CommandOutput "deployment smoke verification" (Join-Path $evidenceDir "verify-deployment.txt") {
    & (Join-Path $PSScriptRoot "verify-deployment.ps1") -BaseUrl $BaseUrl -Origin $Origin
}
Save-AcceptanceSummary $evidenceDir $BaseUrl $Origin
Save-CommandOutput "backend logs tail" (Join-Path $evidenceDir "backend-logs-tail.txt") {
    Push-Location $BackendRoot
    try {
        docker compose --env-file $envPath logs --tail 200 backend
    } finally {
        Pop-Location
    }
}
Save-CommandOutput "mysql logs tail" (Join-Path $evidenceDir "mysql-logs-tail.txt") {
    Push-Location $BackendRoot
    try {
        docker compose --env-file $envPath logs --tail 100 mysql
    } finally {
        Pop-Location
    }
}

Write-Host "Deployment evidence collected at $evidenceDir"
