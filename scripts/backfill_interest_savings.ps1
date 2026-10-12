# Demo recovery using one current savings-balance capture; not historical reconstruction.
# Run on the computer hosting this Docker stack. No monthly posting is invoked.
param(
    [string]$ApiBase = 'http://localhost:3001/api/v1',
    [string]$ThroughDate = [DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(8)).Date.AddDays(-2).ToString('yyyy-MM-dd'),
    [System.Management.Automation.PSCredential]$Credential,
    [string]$PostgresContainer = 'postgres-immutable-audit',
    [string]$PostgresUser = 'audit_user',
    [string]$PostgresDatabase = 'ledger_audit_db'
)
$ErrorActionPreference = 'Stop'
$culture = [Globalization.CultureInfo]::InvariantCulture
$through = [datetime]::ParseExact($ThroughDate, 'yyyy-MM-dd', $culture)
$today = [DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(8)).Date
if ($through -ge $today) { throw 'ThroughDate must be before today in Asia/Manila.' }
$periodEnd = $through.AddDays([datetime]::DaysInMonth($through.Year, $through.Month) - $through.Day).ToString('yyyy-MM-dd')
$ApiBase = $ApiBase.TrimEnd('/')
if (!$Credential) { $Credential = Get-Credential -Message 'Admin login for the local PayPink demo' }
if (!$Credential) { throw 'Admin credentials are required.' }
$loginBody = @{ username = $Credential.UserName; password = $Credential.GetNetworkCredential().Password } | ConvertTo-Json
try {
    $login = Invoke-RestMethod -Method Post -Uri "$ApiBase/auth/login" -ContentType 'application/json' -Body $loginBody -TimeoutSec 30
} finally { $loginBody = $null }
if (!$login.token -or $login.roles -notcontains 'ROLE_ADMIN') { throw 'An administrator login is required.' }
$headers = @{ Authorization = "Bearer $($login.token)" }
function Invoke-InterestApi([string]$Path, $Body) {
    $requestOptions = @{ Uri = "$ApiBase/interest/eod$Path"; Headers = $headers; TimeoutSec = 120 }
    if ($null -ne $Body) {
        $requestOptions.Method = 'Post'; $requestOptions.ContentType = 'application/json'
        $requestOptions.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }
    Invoke-RestMethod @requestOptions
}
try {
    $overview = Invoke-InterestApi "/overview?periodEnd=$periodEnd"
    if ($through -ge [datetime]::ParseExact($overview.businessDate, 'yyyy-MM-dd', $culture)) {
        throw 'ThroughDate must also be before the service business date.'
    }
    $missing = @($overview.missingDays | Where-Object { $_ -le $ThroughDate } | Sort-Object)
    $accounts = @()
    if ($missing.Count) {
        # No pagination: this admin endpoint returns the complete account list.
        # Assign first: wrapping Invoke-RestMethod directly in @() nests its JSON array
        # in Windows PowerShell, so filters would receive the entire list as one account.
        $accountResponse = Invoke-RestMethod -Uri "$ApiBase/accounts" -Headers $headers -TimeoutSec 120
        $allAccounts = @($accountResponse | Where-Object { $null -ne $_ })
        if (!$allAccounts.Count) { throw 'Account list is empty; refusing to seal empty days without account evidence.' }
        $accounts = @($allAccounts | Where-Object { $_.status -eq 'ACTIVE' -and $_.accountType -in @('SAVINGS','SAVINGS_ACCOUNT') } | Sort-Object accountId)
        if (!$accounts.Count) { throw 'No active savings accounts found; no dates were backfilled.' }
        $seen = @{}
        foreach ($account in $accounts) {
            if (!$account.accountId -or $seen.ContainsKey([string]$account.accountId) -or $account.currency -ne 'PHP' -or $account.mayBeStale -or
                $null -eq $account.currentBalance -or [decimal]$account.currentBalance -lt 0 -or !$account.createdDate) {
                throw 'Invalid, duplicate, non-PHP, or stale savings account data; no dates were backfilled.'
            }
            $seen[[string]$account.accountId] = $true
            # API LocalDateTime values are UTC; compare with each Manila end-of-day boundary.
            $opened = [DateTimeOffset]::Parse($account.createdDate, $culture, [Globalization.DateTimeStyles]::AssumeUniversal)
            $account | Add-Member -NotePropertyName OpenedUtc -NotePropertyValue $opened.UtcDateTime
        }
    }

    # Existing volumes do not rerun Docker initialization migrations.
    $migration = Join-Path $PSScriptRoot 'migrate_interest_single_admin_postgres.sql'
    Get-Content -Raw -LiteralPath $migration | docker exec -i $PostgresContainer psql -v ON_ERROR_STOP=1 -U $PostgresUser -d $PostgresDatabase
    if ($LASTEXITCODE -ne 0) { throw 'Approval migration failed; no backfill requests were submitted.' }

    $captured = [DateTimeOffset]::UtcNow.ToString('o')
    foreach ($dateText in $missing) {
        $date = [datetime]::ParseExact($dateText, 'yyyy-MM-dd', $culture)
        $cutoffUtc = [datetime]::SpecifyKind($date.AddDays(1).AddHours(-8), [DateTimeKind]::Utc)
        $manifest = @($accounts | Where-Object { $_.OpenedUtc -lt $cutoffUtc } | ForEach-Object {
            @{ accountId = [long]$_.accountId; accountType = 'SAVINGS'; eodBalance = [decimal]$_.currentBalance }
        })
        $body = @{
            mode = 'BACKFILL'; confirmed = $true; accounts = $manifest
            sourceReference = "current-savings-balance-capture/$captured"
            reason = "Demo backfill requested using current savings balances captured at $captured. Reused for missing dates; not reconstructed historical balances. Accounts opened after each date are excluded."
        }
        $proposal = Invoke-InterestApi "/resolve?businessDate=$dateText" $body
        $review = Invoke-InterestApi "/backfills/$($proposal.proposalId)"
        if ($review.businessDate -ne $dateText -or $review.request.sourceReference -ne $body.sourceReference -or
            $review.preparedBy -ine $login.username -or $review.requestHash -ne $proposal.requestHash -or
            @($review.request.accounts).Count -ne $manifest.Count) {
            throw "Stored proposal review does not match the submitted capture for $dateText."
        }
        $reviewRows = @($review.request.accounts | Sort-Object accountId)
        $expectedRows = @($manifest | Sort-Object { $_.accountId })
        for ($i = 0; $i -lt $expectedRows.Count; $i++) {
            if ($reviewRows[$i].accountId -ne $expectedRows[$i].accountId -or
                $reviewRows[$i].accountType -ne $expectedRows[$i].accountType -or
                [decimal]$reviewRows[$i].eodBalance -ne $expectedRows[$i].eodBalance) {
                throw "Stored proposal balances do not match the capture for $dateText."
            }
        }
        $result = Invoke-InterestApi "/backfills/$($proposal.proposalId)/approve" @{
            confirmed = $true
            reason = 'Reviewed the stored current-savings capture and account creation dates for the requested demo backfill.'
        }
        if ($result.businessDate -ne $dateText) { throw "Unexpected approval result for $dateText." }
        Write-Host "$dateText completed ($($manifest.Count) savings accounts)."
    }
    $final = Invoke-InterestApi "/overview?periodEnd=$periodEnd"
    $remaining = @($final.missingDays | Where-Object { $_ -le $ThroughDate })
    if ($remaining.Count) { throw "Days still missing through ${ThroughDate}: $($remaining -join ', ')" }
    Write-Host "Verified: no missing days in the selected interest month through $ThroughDate. Monthly status: $($final.postingStatus)."
} finally {
    $headers.Clear(); $login = $null
}
