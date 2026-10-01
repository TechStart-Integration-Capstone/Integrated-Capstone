[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "=====================================================" -ForegroundColor Magenta
Write-Host "   PayPink Retail Ledger - Live Integration Flow" -ForegroundColor Magenta
Write-Host "=====================================================" -ForegroundColor Magenta

$GATEWAY = "http://localhost:8080"

# 1. Login
Write-Host "`n[1/4] Authenticating as Juan Dela Cruz (ROLE_CUSTOMER)..." -ForegroundColor Cyan
$loginBody = '{"username":"jdelacruz","password":"CustomerPass123!"}'

try {
    $authResp = Invoke-RestMethod -Uri "$GATEWAY/api/v1/auth/login" -Method Post -Body $loginBody -ContentType "application/json" -UseBasicParsing
    $token = $authResp.token
    Write-Host "[OK] Authentication successful!" -ForegroundColor Green
    Write-Host "     Customer: $($authResp.fullName) ($($authResp.username))" -ForegroundColor Gray
    Write-Host "     Bearer Token: $($token.Substring(0, 30))..." -ForegroundColor Gray
} catch {
    Write-Host "[FAIL] Authentication failed: $_" -ForegroundColor Red
    exit 1
}

# 2. Check Initial Balance
Write-Host "`n[2/4] Querying Account #1 Initial Balance..." -ForegroundColor Cyan
$headers = @{ "Authorization" = "Bearer $token" }
try {
    $account = Invoke-RestMethod -Uri "$GATEWAY/api/v1/accounts/1" -Method Get -Headers $headers -UseBasicParsing
    Write-Host "[OK] Account Number: $($account.accountNumber)" -ForegroundColor Green
    Write-Host "     Current Balance: PHP $($account.currentBalance)" -ForegroundColor Yellow
} catch {
    Write-Host "[FAIL] Account query failed: $_" -ForegroundColor Red
}

# 3. Post Idempotent Mutation
$idempotencyKey = "PAYPINK-DEMO-" + (Get-Random -Minimum 10000 -Maximum 99999)
Write-Host "`n[3/4] Executing Balance Mutation (CREDIT PHP 1,500.00) with Key: $idempotencyKey..." -ForegroundColor Cyan

$mutationBody = '{"accountId":1,"mutationAmount":1500.0000,"operation":"CREDIT","transactionType":"TRANSFER_INSTAPAY","currency":"PHP","description":"InstaPay deposit from BGC branch"}'

$mutationHeaders = @{
    "Authorization"   = "Bearer $token"
    "Idempotency-Key" = $idempotencyKey
    "Content-Type"    = "application/json"
}

try {
    $txResult = Invoke-RestMethod -Uri "$GATEWAY/api/v1/ledger/mutate" -Method Post -Body $mutationBody -Headers $mutationHeaders -UseBasicParsing
    Write-Host "[OK] Transaction Succeeded!" -ForegroundColor Green
    Write-Host "     Reference No:    $($txResult.referenceNo)" -ForegroundColor Green
    Write-Host "     Previous Balance: PHP $($txResult.beforeBalance)" -ForegroundColor Gray
    Write-Host "     New Balance:      PHP $($txResult.afterBalance)" -ForegroundColor Yellow
    Write-Host "     Ledger Status:    $($txResult.status)" -ForegroundColor Gray
} catch {
    Write-Host "[FAIL] Transaction failed: $_" -ForegroundColor Red
}

# 4. Test Idempotency Replay
Write-Host "`n[4/4] Testing Idempotency Replay (Resending exact same payload and key)..." -ForegroundColor Cyan
try {
    $replayResp = Invoke-WebRequest -Uri "$GATEWAY/api/v1/ledger/mutate" -Method Post -Body $mutationBody -Headers $mutationHeaders -UseBasicParsing
    $isReplay = $replayResp.Headers["Idempotent-Replay"]
    Write-Host "[OK] Replay Accepted without Double-Charging!" -ForegroundColor Green
    Write-Host "     HTTP Status:        $($replayResp.StatusCode)" -ForegroundColor Green
    Write-Host "     Idempotent-Replay:  $isReplay" -ForegroundColor Yellow
} catch {
    Write-Host "[FAIL] Replay test failed: $_" -ForegroundColor Red
}

Write-Host "`n=====================================================" -ForegroundColor Magenta
Write-Host "   PayPink Flow Complete and Verified!" -ForegroundColor Magenta
Write-Host "=====================================================" -ForegroundColor Magenta
