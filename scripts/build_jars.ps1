# Build all 9 microservice JARs for Docker deployment
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$settings = Join-Path $PSScriptRoot "settings-clean.xml"

$services = @(
    "api-gateway",
    "auth-service",
    "account-service",
    "transaction-service",
    "notification-service",
    "audit-service",
    "reconciliation-service",
    "outbox-publisher",
    "analytics-service"
)

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " Building PayPink Microservices JARs" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

foreach ($svc in $services) {
    $dir = Join-Path $root "microservices\$svc"
    Write-Host "`n>>> Building $svc..." -ForegroundColor Yellow
    Push-Location $dir
    try {
        mvn clean package -s $settings -DskipTests
        if ($LASTEXITCODE -ne 0) {
            throw "Build failed for $svc"
        }
        Write-Host ">>> Successfully built $svc" -ForegroundColor Green
    }
    finally {
        Pop-Location
    }
}

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host " All 9 Microservices Built Successfully!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
