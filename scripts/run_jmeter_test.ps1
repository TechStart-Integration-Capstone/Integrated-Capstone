# PayPink 2.0 - Automated JMeter CLI Runner with Auto-Cleanup and Browser Popup
param(
    [int]$Threads = 20,
    [int]$Loops = 10,
    [int]$RampUp = 2
)

Write-Host "======================================================================" -ForegroundColor Magenta
Write-Host "  PayPink 2.0 - T24 Core Remittance Saga Performance Runner           " -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Magenta
Write-Host "Configuration:" -ForegroundColor Gray
Write-Host "  Threads (Users)    : $Threads"
Write-Host "  Loops Per Thread   : $Loops (Total Transfers: $($Threads * $Loops))"
Write-Host "  Ramp-up (seconds)  : $RampUp"
Write-Host ""

# 1. Clean previous results folder so JMeter does not throw folder is not empty
Write-Host "Cleaning up previous results..." -ForegroundColor Yellow
if (Test-Path "performance\results") {
    Remove-Item -Recurse -Force "performance\results\*" -ErrorAction SilentlyContinue
}

# 2. Run JMeter in Non-GUI mode
Write-Host "Executing JMeter T24 Remittance Saga Test Plan..." -ForegroundColor Green
jmeter -n -t performance/PayPink_T24_Remittance_Saga.jmx "-Jthreads=$Threads" "-Jloops=$Loops" "-Jrampup=$RampUp" -l performance/results/saga_results.jtl -e -o performance/results/report

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "Test completed successfully!" -ForegroundColor Green
    Write-Host "Opening HTML Dashboard Report in your browser..." -ForegroundColor Cyan
    Start-Process "performance\results\report\index.html"
} else {
    Write-Host "JMeter encountered an issue. Check logs above." -ForegroundColor Red
}
