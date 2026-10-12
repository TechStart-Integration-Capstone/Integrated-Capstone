# Offline orchestration regressions: HTTP and Docker are mocked; no databases are accessed.
$ErrorActionPreference = 'Stop'
$global:interestBackfillTestState = @{}
$runner = Join-Path $PSScriptRoot 'backfill_interest_savings.ps1'
$secret = ConvertTo-SecureString ([guid]::NewGuid().ToString()) -AsPlainText -Force
$credential = New-Object System.Management.Automation.PSCredential('admin', $secret)
function Assert-Backfill($Condition, [string]$Message) { if (!$Condition) { throw $Message } }
function docker {
    $global:interestBackfillTestState.migrations++
    $global:LASTEXITCODE = if ($global:interestBackfillTestState.scenario -eq 'migration-failure') { 1 } else { 0 }
}
function Invoke-RestMethod {
    param($Method, $Uri, $ContentType, $Body, $TimeoutSec, $Headers)
    $global:interestBackfillTestState.calls.Add([string]$Uri)
    if ($Uri -match '/auth/login$') {
        return @{token='offline-test-token';username='admin';roles=@('ROLE_ADMIN')}
    }
    Assert-Backfill ($Headers.Authorization -eq 'Bearer offline-test-token') 'Missing authenticated admin session.'
    if ($Uri -match '/overview\?') {
        $remaining = @('2020-10-01','2020-10-02','2020-10-03') | Where-Object { !$global:interestBackfillTestState.completed.Contains($_) }
        return @{businessDate='2020-10-04';missingDays=@($remaining);postingStatus='ACCRUING'}
    }
    if ($Uri -match '/accounts$') {
        # Invoke-RestMethod emits a JSON array as one pipeline object in Windows PowerShell.
        $response = @(
            [pscustomobject]@{accountId=1;accountType='SAVINGS';currency='PHP';status='ACTIVE';currentBalance=15000.1250;createdDate='2020-09-30T16:00:00';mayBeStale=($global:interestBackfillTestState.scenario -eq 'stale')},
            [pscustomobject]@{accountId=2;accountType='SAVINGS_ACCOUNT';currency='PHP';status='ACTIVE';currentBalance=123.45;createdDate='2020-10-01T16:00:00'},
            [pscustomobject]@{accountId=3;accountType='INTERNAL';currency='PHP';status='ACTIVE';currentBalance=50000;createdDate='2020-09-01T00:00:00'},
            [pscustomobject]@{accountId=4;accountType='SAVINGS';currency='PHP';status='CLOSED';currentBalance=0;createdDate='2020-09-01T00:00:00'},
            [pscustomobject]@{accountId=5;accountType='LOAN';currency='PHP';status='ACTIVE';currentBalance=25000;createdDate='2020-09-01T00:00:00'}
        )
        if ($global:interestBackfillTestState.scenario -eq 'empty') { $response = @() }
        if ($global:interestBackfillTestState.scenario -eq 'no-savings') { $response = @($response | Where-Object { $_.accountType -in @('INTERNAL','LOAN') }) }
        if ($global:interestBackfillTestState.scenario -eq 'single') { $response = @($response[0]) }
        return ,$response
    }
    if ($Uri -match '/resolve\?businessDate=(.+)$') {
        $date = $Matches[1]
        $request = $Body | ConvertFrom-Json
        Assert-Backfill ($request.sourceReference -like 'current-savings-balance-capture/*') 'Capture source must be truthful.'
        Assert-Backfill ($request.reason -match 'not reconstructed historical') 'Capture provenance must be retained.'
        $id = [guid]::NewGuid().ToString()
        $proposal = @{proposalId=$id;businessDate=$date;request=$request;requestHash='offline-hash';preparedBy='admin'}
        $global:interestBackfillTestState.proposals[$id] = $proposal
        return $proposal
    }
    if ($Uri -match '/backfills/([^/]+)/approve$') {
        $proposal = $global:interestBackfillTestState.proposals[$Matches[1]]
        $approval = $Body | ConvertFrom-Json
        Assert-Backfill ($approval.confirmed -and $approval.reason) 'Separate approval confirmation and reason required.'
        if ($global:interestBackfillTestState.scenario -ne 'verification-failure') { [void]$global:interestBackfillTestState.completed.Add($proposal.businessDate) }
        return @{businessDate=$proposal.businessDate}
    }
    if ($Uri -match '/backfills/([^/]+)$') {
        $proposal = $global:interestBackfillTestState.proposals[$Matches[1]]
        if ($global:interestBackfillTestState.scenario -eq 'review-mismatch') { $proposal.request.accounts[0].eodBalance = 999 }
        return $proposal
    }
    throw "Unexpected request: $Uri"
}
foreach ($case in @('success','single','empty','no-savings','already-complete','stale','migration-failure','review-mismatch','verification-failure')) {
    $global:interestBackfillTestState.scenario = $case
    $global:interestBackfillTestState.migrations = 0
    $global:interestBackfillTestState.calls = New-Object 'System.Collections.Generic.List[string]'
    $global:interestBackfillTestState.completed = New-Object 'System.Collections.Generic.HashSet[string]'
    $global:interestBackfillTestState.proposals = @{}
    if ($case -eq 'already-complete') { [void]$global:interestBackfillTestState.completed.Add('2020-10-01'); [void]$global:interestBackfillTestState.completed.Add('2020-10-02') }
    $failure = $null
    try { & $runner -Credential $credential -ThroughDate '2020-10-02' } catch { $failure = $_.Exception.Message }
    if ($case -in @('success','single','already-complete')) {
        Assert-Backfill (!$failure) "Unexpected failure: $failure"
        Assert-Backfill ($global:interestBackfillTestState.completed.Count -eq 2) 'Exactly the requested elapsed days must be complete.'
        if ($case -in @('success','single')) {
            $first = @($global:interestBackfillTestState.proposals.Values | Where-Object { $_.businessDate -eq '2020-10-01' })[0]
            $second = @($global:interestBackfillTestState.proposals.Values | Where-Object { $_.businessDate -eq '2020-10-02' })[0]
            Assert-Backfill ($first.request.accounts.Count -eq 1) 'Exclude accounts opened after the Manila date cutoff.'
            $expectedCount = if ($case -eq 'single') { 1 } else { 2 }
            Assert-Backfill ($second.request.accounts.Count -eq $expectedCount) 'Handle singleton and multi-account JSON arrays.'
            Assert-Backfill (@($second.request.accounts | Where-Object { $_.accountType -ne 'SAVINGS' }).Count -eq 0) 'Normalize SAVINGS_ACCOUNT to SAVINGS.'
            Assert-Backfill ([decimal]$first.request.accounts[0].eodBalance -eq [decimal]15000.1250) 'Keep decimal balances.'
        } else { Assert-Backfill ($global:interestBackfillTestState.proposals.Count -eq 0) 'A rerun must not re-file completed dates.' }
    } else {
        $expected = switch ($case) {
            'empty' { 'Account list is empty' }
            'no-savings' { 'No active savings accounts' }
            'stale' { 'stale savings account data' }
            'migration-failure' { 'Approval migration failed' }
            'review-mismatch' { 'Stored proposal balances' }
            'verification-failure' { 'Days still missing' }
        }
        Assert-Backfill ($failure -and $failure.Contains($expected)) "Unexpected error for ${case}: $failure"
        if ($case -ne 'verification-failure') { Assert-Backfill ($global:interestBackfillTestState.completed.Count -eq 0) 'Failed validation must not approve a day.' }
    }
    Assert-Backfill (@($global:interestBackfillTestState.calls | Where-Object { $_ -match '/post(?:\?|$)' }).Count -eq 0) 'Never invoke monthly posting.'
    Write-Host "PASS: $case"
}
Remove-Variable -Name interestBackfillTestState -Scope Global
