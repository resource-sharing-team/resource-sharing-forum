param(
    [string]$EnvFile = "./.env"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Environment file not found: $EnvFile"
}

$required = @(
    "MYSQL_ROOT_PASSWORD",
    "MYSQL_APP_USER",
    "MYSQL_APP_PASSWORD",
    "MYSQL_DATABASE",
    "CORS_ALLOWED_ORIGINS",
    "JWT_SECRET"
)

$values = @{}
Get-Content -LiteralPath $EnvFile | ForEach-Object {
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
    $values[$key] = $value
}

foreach ($key in $required) {
    if (-not $values.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($values[$key])) {
        throw "$key is required in $EnvFile"
    }
}

function Test-Placeholder {
    param([string]$Value)
    $normalized = $Value.ToLowerInvariant()
    return $normalized.Contains("change-this") -or $normalized.Contains("replace-with") -or $normalized.Contains("<")
}

function Assert-StrongSecret {
    param(
        [string]$Name,
        [string]$Value,
        [int]$MinLength
    )

    $weakValues = @("root", "admin", "password", "123456")
    if (Test-Placeholder $Value) {
        throw "$Name must be replaced before deployment"
    }
    if ($weakValues -contains $Value.ToLowerInvariant()) {
        throw "$Name must not use a common weak value"
    }
    if ($Value.Length -lt $MinLength) {
        throw "$Name must contain at least $MinLength characters"
    }
}

Assert-StrongSecret "MYSQL_ROOT_PASSWORD" $values["MYSQL_ROOT_PASSWORD"] 12
Assert-StrongSecret "MYSQL_APP_PASSWORD" $values["MYSQL_APP_PASSWORD"] 12
Assert-StrongSecret "JWT_SECRET" $values["JWT_SECRET"] 32

foreach ($origin in $values["CORS_ALLOWED_ORIGINS"].Split(",")) {
    $value = $origin.Trim()
    if ($value.Length -eq 0) {
        continue
    }
    if ($value -eq "*") {
        throw "CORS_ALLOWED_ORIGINS must not use wildcard origins"
    }
    if (-not ($value.StartsWith("http://") -or $value.StartsWith("https://"))) {
        throw "CORS_ALLOWED_ORIGINS must contain HTTP(S) origins"
    }
}

Write-Host "Environment validation passed."
