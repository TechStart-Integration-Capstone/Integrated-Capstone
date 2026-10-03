param(
    [string]$JdbcUrl = $env:AZURE_SQL_JDBC_URL,
    [string]$EnvFile,
    [switch]$SkipTcpCheck
)

$ErrorActionPreference = "Stop"

function Get-JdbcProperty {
    param(
        [string[]]$Parts,
        [string]$Name
    )

    $match = $Parts | Where-Object { $_ -match "^(?i)$([regex]::Escape($Name))=" } | Select-Object -First 1
    if (-not $match) {
        return $null
    }

    return ($match -split "=", 2)[1]
}

if ([string]::IsNullOrWhiteSpace($JdbcUrl)) {
    $candidateEnvFiles = @()
    if (-not [string]::IsNullOrWhiteSpace($EnvFile)) {
        $candidateEnvFiles += $EnvFile
    }
    $candidateEnvFiles += @(
        (Join-Path (Get-Location) ".env"),
        (Join-Path $PSScriptRoot "..\docker\.env")
    )

    foreach ($candidate in $candidateEnvFiles) {
        if (-not (Test-Path -LiteralPath $candidate)) {
            continue
        }

        $line = Get-Content -LiteralPath $candidate |
            Where-Object { $_ -match "^\s*AZURE_SQL_JDBC_URL\s*=" } |
            Select-Object -First 1

        if ($line) {
            $JdbcUrl = ($line -split "=", 2)[1].Trim().Trim('"').Trim("'")
            Write-Host "Loaded AZURE_SQL_JDBC_URL from $candidate"
            break
        }
    }
}

if ([string]::IsNullOrWhiteSpace($JdbcUrl)) {
    Write-Error "AZURE_SQL_JDBC_URL is not set. Set it in the shell or in docker/.env before starting the services."
}

$maskedUrl = $JdbcUrl `
    -replace "(?i)(password|pwd)=([^;]+)", '$1=***' `
    -replace "(?i)(user|username)=([^;]+)", '$1=***'

Write-Host "Using JDBC URL: $maskedUrl"

if ($JdbcUrl -notmatch "^jdbc:sqlserver://(?<host>[^:;]+)(:(?<port>\d+))?(?<rest>;.*)?$") {
    Write-Error "AZURE_SQL_JDBC_URL is not a SQL Server JDBC URL."
}

$server = $Matches["host"]
$portText = $Matches["port"]
$port = if ([string]::IsNullOrWhiteSpace($portText)) { 1433 } else { [int]$portText }
$parts = $JdbcUrl.Substring($JdbcUrl.IndexOf(";") + 1).Split(";", [System.StringSplitOptions]::RemoveEmptyEntries)
$database = Get-JdbcProperty -Parts $parts -Name "databaseName"
if ([string]::IsNullOrWhiteSpace($database)) {
    $database = Get-JdbcProperty -Parts $parts -Name "database"
}
$authentication = Get-JdbcProperty -Parts $parts -Name "authentication"

Write-Host "Server: $server"
Write-Host "Port: $port"
Write-Host "Database: $(if ($database) { $database } else { '<not specified>' })"
Write-Host "Authentication: $(if ($authentication) { $authentication } else { '<JDBC default or SQL auth properties>' })"

if ($authentication -eq "ActiveDirectoryMSI" -and
    [string]::IsNullOrWhiteSpace($env:IDENTITY_ENDPOINT) -and
    [string]::IsNullOrWhiteSpace($env:MSI_ENDPOINT)) {
    Write-Warning "ActiveDirectoryMSI normally works only from an Azure host with a managed identity endpoint. Local Docker usually needs a different supported auth mode."
}

if (-not $SkipTcpCheck) {
    Write-Host "Checking TCP reachability to ${server}:$port ..."
    $result = Test-NetConnection -ComputerName $server -Port $port -InformationLevel Quiet
    if (-not $result) {
        Write-Error "Cannot reach ${server}:$port. Check Azure SQL firewall/network rules and local network access."
    }
}

Write-Host "Azure SQL connection settings look usable from this host."
