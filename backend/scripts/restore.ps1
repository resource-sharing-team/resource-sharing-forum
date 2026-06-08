param(
    [Parameter(Mandatory = $true)]
    [string]$DatabaseBackup,
    [string]$UploadBackup,
    [string]$EnvFile = "./.env",
    [string]$Database = $(if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "resource_sharing_forum" }),
    [string]$UploadVolume = "resource-sharing-forum_backend_uploads"
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

function Import-EnvFile {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Environment file not found: $Path"
    }
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith("#")) {
            return
        }
        $index = $line.IndexOf("=")
        if ($index -le 0) {
            return
        }
        $key = $line.Substring(0, $index).Trim()
        $value = $line.Substring($index + 1).Trim().Trim('"').Trim("'")
        [Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
}

function Assert-DockerAvailable {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker command is required for restore"
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

$envPath = Resolve-BackendPath $EnvFile
$databaseBackupCandidate = Resolve-BackendPath $DatabaseBackup

& (Join-Path $PSScriptRoot "validate-env.ps1") -EnvFile $envPath
Import-EnvFile $envPath
if ($env:MYSQL_DATABASE) {
    $Database = $env:MYSQL_DATABASE
}

$databaseBackupPath = (Resolve-Path -LiteralPath $databaseBackupCandidate).Path
$uploadBackupPath = $null
$uploadBackupDir = $null
$uploadBackupName = $null
if ($UploadBackup) {
    $uploadBackupCandidate = Resolve-BackendPath $UploadBackup
    $uploadBackupPath = (Resolve-Path -LiteralPath $uploadBackupCandidate).Path
    $uploadBackupDir = Split-Path -Parent $uploadBackupPath
    $uploadBackupName = Split-Path -Leaf $uploadBackupPath
}
Assert-DockerAvailable

Push-Location $BackendRoot
try {
    Get-Content -LiteralPath $databaseBackupPath -Encoding UTF8 |
        docker compose --env-file $envPath exec -T mysql mysql -u root -p$env:MYSQL_ROOT_PASSWORD --default-character-set=utf8mb4 $Database
    Assert-LastExitCode "Database restore"

    if ($UploadBackup) {
        docker run --rm -v "${UploadVolume}:/data" -v "${uploadBackupDir}:/restore:ro" alpine:3.20 sh -c "rm -rf /data/* && tar -xf /restore/$uploadBackupName -C /data"
        Assert-LastExitCode "Upload restore"
    }
} finally {
    Pop-Location
}

Write-Host "Restore completed for database: $Database"
