param(
    [string]$EnvFile = "./.env",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Origin = "http://localhost:5173",
    [string]$EvidenceDir = "./deployment-evidence",
    [string]$FrontendDir = "",
    [int]$ReadinessTimeoutSeconds = 120,
    [switch]$SkipBuild,
    [switch]$SkipFrontendSmoke
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
        throw "Docker command is required for production acceptance verification"
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

$envPath = Resolve-BackendPath $EnvFile
$evidencePath = Resolve-BackendPath $EvidenceDir
$evidencePathFile = Join-Path $evidencePath ".latest-production-evidence-path.txt"

$deployArgs = @{
    EnvFile = $envPath
    BaseUrl = $BaseUrl
    Origin = $Origin
    ReadinessTimeoutSeconds = $ReadinessTimeoutSeconds
    Verify = $true
}
if ($SkipBuild) {
    Write-Host "Skipping docker compose build/start; running smoke verification against existing stack."
    & (Join-Path $PSScriptRoot "validate-env.ps1") -EnvFile $envPath
    Assert-DockerComposeAvailable
    & (Join-Path $PSScriptRoot "verify-deployment.ps1") -BaseUrl $BaseUrl -Origin $Origin
} else {
    & (Join-Path $PSScriptRoot "deploy.ps1") @deployArgs
}

& (Join-Path $PSScriptRoot "collect-deployment-evidence.ps1") `
    -EnvFile $envPath `
    -BaseUrl $BaseUrl `
    -Origin $Origin `
    -OutputDir $evidencePath `
    -EvidencePathFile $evidencePathFile

$collectedEvidenceDir = (Get-Content -LiteralPath $evidencePathFile -Raw -Encoding UTF8).Trim()

if (-not $SkipFrontendSmoke -and -not [string]::IsNullOrWhiteSpace($FrontendDir)) {
    $frontendCandidate = if ([System.IO.Path]::IsPathRooted($FrontendDir)) { $FrontendDir } else { Join-Path $BackendRoot $FrontendDir }
    $frontendPath = Resolve-Path -LiteralPath $frontendCandidate
    $npmCommand = Resolve-NpmCommand
    Write-Host "Running frontend real-backend smoke against production backend at $BaseUrl..."
    $frontendSmokeLog = Join-Path $collectedEvidenceDir "frontend-smoke.txt"
    $previousViteApiBaseUrl = $env:VITE_API_BASE_URL
    $previousViteApiPrefix = $env:VITE_API_PREFIX
    $previousViteEnableMocks = $env:VITE_ENABLE_MOCKS
    try {
        $env:VITE_API_BASE_URL = $BaseUrl
        $env:VITE_API_PREFIX = "/api"
        $env:VITE_ENABLE_MOCKS = "false"
        Push-Location $frontendPath
        try {
            $global:LASTEXITCODE = 0
            & $npmCommand run test:e2e:backend *>&1 | Tee-Object -FilePath $frontendSmokeLog
            Assert-LastExitCode "Frontend production backend smoke"
            Save-AcceptanceSummary `
                -EvidenceDir $collectedEvidenceDir `
                -BaseUrl $BaseUrl `
                -Origin $Origin `
                -FrontendSmokeStatus "PASSED" `
                -FrontendSmokeLog "frontend-smoke.txt"
        } catch {
            try {
                Save-AcceptanceSummary `
                    -EvidenceDir $collectedEvidenceDir `
                    -BaseUrl $BaseUrl `
                    -Origin $Origin `
                    -FrontendSmokeStatus "FAILED" `
                    -FrontendSmokeLog "frontend-smoke.txt" `
                    -FrontendSmokeMessage $_.Exception.Message
            } catch {
                Write-Host $_
            }
            throw
        } finally {
            Pop-Location
        }
    } finally {
        $env:VITE_API_BASE_URL = $previousViteApiBaseUrl
        $env:VITE_API_PREFIX = $previousViteApiPrefix
        $env:VITE_ENABLE_MOCKS = $previousViteEnableMocks
    }
} else {
    $frontendMessage = if ($SkipFrontendSmoke) { "Frontend smoke skipped by -SkipFrontendSmoke" } else { "Frontend smoke not requested" }
    Save-AcceptanceSummary `
        -EvidenceDir $collectedEvidenceDir `
        -BaseUrl $BaseUrl `
        -Origin $Origin `
        -FrontendSmokeStatus "SKIPPED" `
        -FrontendSmokeMessage $frontendMessage
}

Write-Host "Production acceptance verification passed."
