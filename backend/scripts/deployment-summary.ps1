function Save-AcceptanceSummary {
    param(
        [string]$EvidenceDir,
        [string]$BaseUrl,
        [string]$Origin,
        [string]$FrontendSmokeStatus = "NOT_REQUESTED",
        [string]$FrontendSmokeLog = "",
        [string]$FrontendSmokeMessage = ""
    )

    $healthRecord = Get-Content -LiteralPath (Join-Path $EvidenceDir "api-health.json") -Encoding UTF8 | ConvertFrom-Json
    $healthBody = $healthRecord.body | ConvertFrom-Json
    $smokeOutput = Get-Content -LiteralPath (Join-Path $EvidenceDir "verify-deployment.txt") -Raw -Encoding UTF8
    $backendPassed = [int]$healthRecord.statusCode -eq 200 `
        -and $healthBody.code -eq 200 `
        -and $healthBody.data.status -eq "UP" `
        -and $healthBody.data.database -eq "UP" `
        -and $smokeOutput.Contains("Deployment verification passed.")
    $frontendPassed = $FrontendSmokeStatus -eq "PASSED"
    $frontendFailed = $FrontendSmokeStatus -eq "FAILED"
    $passed = $backendPassed -and -not $frontendFailed

    $summary = [ordered]@{
        passed = $passed
        baseUrl = $BaseUrl
        origin = $Origin
        healthHttpStatus = [int]$healthRecord.statusCode
        healthCode = $healthBody.code
        healthStatus = $healthBody.data.status
        healthDatabase = $healthBody.data.database
        smokePassed = $smokeOutput.Contains("Deployment verification passed.")
        frontendSmokeStatus = $FrontendSmokeStatus
        frontendSmokePassed = $frontendPassed
        frontendSmokeLog = $FrontendSmokeLog
        frontendSmokeMessage = $FrontendSmokeMessage
        generatedAt = (Get-Date).ToString("o")
    }
    $summary | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $EvidenceDir "deployment-acceptance-summary.json") -Encoding UTF8

    if (-not $passed) {
        throw "Deployment acceptance summary failed; health database, deployment smoke, and frontend smoke must pass when required"
    }
}
