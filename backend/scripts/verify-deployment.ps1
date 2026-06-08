param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Origin = "http://localhost:5173",
    [switch]$SkipMain
)

$ErrorActionPreference = "Stop"

function Invoke-WebRequestCompat {
    param(
        [string]$Uri,
        [string]$Method,
        [hashtable]$Headers
    )

    $parameters = @{
        Uri = $Uri
        Method = $Method
        Headers = $Headers
        UseBasicParsing = $true
    }
    if ((Get-Command Invoke-WebRequest).Parameters.ContainsKey("SkipHttpErrorCheck")) {
        $parameters.SkipHttpErrorCheck = $true
    }
    return Invoke-WebRequest @parameters
}

function Invoke-Api {
    param(
        [string]$Path,
        [string]$Method = "GET",
        [hashtable]$Headers = @{}
    )

    $uri = "$BaseUrl$Path"
    try {
        return Invoke-WebRequestCompat -Uri $uri -Method $Method -Headers $Headers
    } catch {
        if ($_.Exception.Response) {
            return $_.Exception.Response
        }
        throw
    }
}

function Assert-Status {
    param(
        [object]$Response,
        [int]$Expected,
        [string]$Label
    )

    $actual = [int]$Response.StatusCode
    if ($actual -ne $Expected) {
        throw "$Label expected HTTP $Expected but got $actual"
    }
}

function Get-HeaderValue {
    param(
        [object]$Headers,
        [string]$Name
    )

    if ($null -eq $Headers) {
        return $null
    }
    if ($Headers -is [System.Net.WebHeaderCollection]) {
        return $Headers.Get($Name)
    }
    if ($Headers.ContainsKey($Name)) {
        $value = $Headers[$Name]
        if ($value -is [array]) {
            return $value[0]
        }
        return $value
    }
    foreach ($key in $Headers.Keys) {
        if ([string]::Equals([string]$key, $Name, [System.StringComparison]::OrdinalIgnoreCase)) {
            $value = $Headers[$key]
            if ($value -is [array]) {
                return $value[0]
            }
            return $value
        }
    }
    return $null
}

function Assert-Header {
    param(
        [object]$Response,
        [string]$Name,
        [string]$Label
    )

    if (-not (Get-HeaderValue $Response.Headers $Name)) {
        throw "$Label missing response header $Name"
    }
}

function Assert-HeaderValue {
    param(
        [object]$Response,
        [string]$Name,
        [string]$Expected,
        [string]$Label
    )

    Assert-Header $Response $Name $Label
    $actual = Get-HeaderValue $Response.Headers $Name
    if ($actual -ne $Expected) {
        throw "$Label response header $Name expected '$Expected' but got '$actual'"
    }
}

function Assert-SecurityHeaders {
    param(
        [object]$Response,
        [string]$Label
    )

    Assert-Header $Response "X-Trace-Id" $Label
    Assert-HeaderValue $Response "X-Content-Type-Options" "nosniff" $Label
    Assert-HeaderValue $Response "X-Frame-Options" "DENY" $Label
    Assert-HeaderValue $Response "Referrer-Policy" "strict-origin-when-cross-origin" $Label
    Assert-HeaderValue $Response "Permissions-Policy" "geolocation=(), microphone=(), camera=()" $Label
    Assert-HeaderValue $Response "Content-Security-Policy" "default-src 'self'; frame-ancestors 'none'" $Label
}

if ($SkipMain) {
    return
}

Write-Host "Verifying backend deployment at $BaseUrl"

$health = Invoke-Api -Path "/api/health"
Assert-Status $health 200 "health"
Assert-SecurityHeaders $health "health"
$healthJson = $health.Content | ConvertFrom-Json
if ($healthJson.code -ne 200 -or $healthJson.data.status -ne "UP" -or $healthJson.data.database -ne "UP") {
    throw "health response wrapper is invalid"
}

$resources = Invoke-Api -Path "/api/v1/resources"
Assert-Status $resources 200 "public resources"
$resourcesJson = $resources.Content | ConvertFrom-Json
if ($resourcesJson.code -ne 200 -or $null -eq $resourcesJson.data.list) {
    throw "resources pagination wrapper is invalid"
}

$protected = Invoke-Api -Path "/api/v1/user/profile"
Assert-Status $protected 401 "protected profile"
Assert-SecurityHeaders $protected "protected profile"

$corsHeaders = @{
    Origin = $Origin
    "Access-Control-Request-Method" = "GET"
}
$cors = Invoke-Api -Path "/api/v1/resources" -Method "OPTIONS" -Headers $corsHeaders
Assert-Status $cors 200 "CORS preflight"
Assert-Header $cors "Access-Control-Allow-Origin" "CORS preflight"

Write-Host "Deployment verification passed."
