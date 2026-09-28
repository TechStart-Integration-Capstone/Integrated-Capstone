# ==============================================================================
# PayPink - Automated JMeter Concurrency & Stress Test Runner
# ==============================================================================

param(
    [int]$Threads = 50,
    [int]$Loops = 20,
    [int]$RampUp = 3,
    [string]$HostTarget = "api-gateway",
    [string]$PortTarget = "8080"
)

Write-Host "======================================================================" -ForegroundColor Magenta
Write-Host "   PayPink Retail Ledger - JMeter Concurrency & Stress Test Runner    " -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Magenta
Write-Host "Configuration:" -ForegroundColor Gray
Write-Host "  Concurrent Threads : $Threads"
Write-Host "  Loops Per Thread   : $Loops (Total Requests: $($Threads * $Loops))"
Write-Host "  Ramp-up Time (s)   : $RampUp"
Write-Host "  Target Gateway     : ${HostTarget}:${PortTarget}"
Write-Host ""

# Clean up previous results & report
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (Test-Path "$ScriptDir\results.jtl") { Remove-Item "$ScriptDir\results.jtl" -Force }
if (Test-Path "$ScriptDir\report") { Remove-Item "$ScriptDir\report" -Recurse -Force }

Write-Host "Building / Checking JMeter Container Runner..." -ForegroundColor Yellow
docker compose -f "$ScriptDir\..\docker-compose.yml" build jmeter

Write-Host "Executing JMeter Test Plan..." -ForegroundColor Green
$JMeterCmd = "jmeter -n -t /jmeter/balance_mutation_stress.jmx -l /jmeter/results.jtl -e -o /jmeter/report -Jhost=$HostTarget -Jport=$PortTarget -Jthreads=$Threads -Jloops=$Loops -Jrampup=$RampUp"

docker compose -f "$ScriptDir\..\docker-compose.yml" run --rm --entrypoint bash jmeter -c "$JMeterCmd"

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "Stress test finished successfully!" -ForegroundColor Green
    Write-Host "HTML Dashboard Report generated at: $ScriptDir\report\index.html" -ForegroundColor Cyan
} else {
    Write-Host "JMeter execution encountered errors." -ForegroundColor Red
}
