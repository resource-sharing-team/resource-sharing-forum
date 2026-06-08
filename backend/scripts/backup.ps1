param(
    [string]$OutputDir = "./backups",
    [string]$EnvFile = "./.env",
    [string]$Database = $(if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "resource_sharing_forum" }),
    [string]$UploadVolume = "resource-sharing-forum_backend_uploads",
    [int]$RetentionDays = 7
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
        throw "Docker command is required for backup"
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
$outputPath = Resolve-BackendPath $OutputDir

& (Join-Path $PSScriptRoot "validate-env.ps1") -EnvFile $envPath
Import-EnvFile $envPath
if ($env:MYSQL_DATABASE) {
    $Database = $env:MYSQL_DATABASE
}
Assert-DockerAvailable

New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$dbBackup = Join-Path $outputPath "$Database-$stamp.sql"
$uploadBackup = Join-Path $outputPath "backend-uploads-$stamp.tar"

Push-Location $BackendRoot
try {
    docker compose --env-file $envPath exec -T mysql mysqldump -u root -p$env:MYSQL_ROOT_PASSWORD --default-character-set=utf8mb4 $Database | Set-Content -LiteralPath $dbBackup -Encoding UTF8
    Assert-LastExitCode "Database backup"
    docker run --rm -v "${UploadVolume}:/data:ro" -v "${outputPath}:/backup" alpine:3.20 tar -cf "/backup/backend-uploads-$stamp.tar" -C /data .
    Assert-LastExitCode "Upload backup"
} finally {
    Pop-Location
}

Get-ChildItem -LiteralPath $outputPath -File |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$RetentionDays) } |
    Remove-Item -Force

Write-Host "Database backup: $dbBackup"
Write-Host "Upload backup:   $uploadBackup"
