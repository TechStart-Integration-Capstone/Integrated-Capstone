# ==============================================================================
# PayPink 2.0: Build all microservices
# ==============================================================================

$services = @(
    "api-gateway",
    "auth-service",
    "account-service",
    "transaction-service",
    "notification-service",
    "audit-service",
    "reconciliation-service",
    "outbox-publisher",
    "analytics-service",
    "t24-adapter",
    "loan-service"
)

$rootDir = Split-Path -Parent $PSScriptRoot

foreach ($s in $services) {
    Write-Host "`n==========================================" -ForegroundColor Cyan
    Write-Host " Building: $s" -ForegroundColor White
    Write-Host "==========================================" -ForegroundColor Cyan
    mvn -f "$rootDir/microservices/$s/pom.xml" clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Build failed for $s"
        exit $LASTEXITCODE
    }
}

Write-Host "`nAll microservices built successfully!" -ForegroundColor Green
