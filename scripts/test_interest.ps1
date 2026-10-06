param([string]$Maven = '', [string]$JavaHome = 'C:/Program Files/Java/jdk-21')
$ErrorActionPreference = 'Stop'
$testRoot = Split-Path $PSScriptRoot -Parent
if (!$Maven) {
    $Maven = (Get-ChildItem "$env:USERPROFILE/.m2/wrapper" -Recurse -Filter mvn.cmd |
        Select-Object -First 1).FullName
}
if (!$Maven) { throw 'Pass -Maven with the path to mvn.cmd.' }
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome/bin;$env:Path"
$testSuffix = [guid]::NewGuid().ToString('N').Substring(0, 10)
$testSqlName = "interest-test-sql-$testSuffix"
$testPgName = "interest-test-pg-$testSuffix"
$testPassword = "It!$([guid]::NewGuid().ToString('N'))"
$env:MSSQL_SA_PASSWORD = $testPassword
$env:POSTGRES_PASSWORD = $testPassword
try {
    docker run --detach --rm --name $testSqlName -e ACCEPT_EULA=Y -e MSSQL_SA_PASSWORD -e MSSQL_PID=Developer -p '127.0.0.1::1433' mcr.microsoft.com/mssql/server:2022-latest | Out-Null
    if ($LASTEXITCODE) { throw 'Could not start disposable SQL Server.' }
    docker run --detach --rm --name $testPgName -e POSTGRES_PASSWORD -e POSTGRES_DB=interest_test -p '127.0.0.1::5432' postgres:15-alpine | Out-Null
    if ($LASTEXITCODE) { throw 'Could not start disposable PostgreSQL.' }
    $testSqlReady = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        # Password is passed through an environment variable, never printed as a command argument.
        $env:SQLCMDPASSWORD = $testPassword
        $ErrorActionPreference = 'Continue'
        docker exec -e SQLCMDPASSWORD $testSqlName /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -l 3 -Q 'IF DB_ID(''interest_test'') IS NULL CREATE DATABASE interest_test;' 2>&1 | Out-Null
        $testProbeExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($testProbeExit -eq 0) { $testSqlReady = $true; break }
        Start-Sleep -Seconds 2
    }
    if (!$testSqlReady) { throw 'Disposable SQL Server did not become ready.' }
    docker exec $testPgName pg_isready -U postgres -d interest_test
    if ($LASTEXITCODE) { throw 'Disposable PostgreSQL did not become ready.' }
    $testSqlPort = (docker port $testSqlName 1433/tcp).Split(':')[-1]
    $testPgPort = (docker port $testPgName 5432/tcp).Split(':')[-1]
    $env:INTEREST_TEST_DATABASES = 'disposable'
    $env:INTEREST_TEST_SQL_URL = "jdbc:sqlserver://127.0.0.1:$testSqlPort;databaseName=interest_test;encrypt=true;trustServerCertificate=true"
    $env:INTEREST_TEST_PG_URL = "jdbc:postgresql://127.0.0.1:$testPgPort/interest_test"
    $env:INTEREST_TEST_PASSWORD = $testPassword
    & $Maven -B -o -f "$testRoot/microservices/transaction-service/pom.xml" test
    if ($LASTEXITCODE) { throw 'Interest/transaction-service tests failed.' }
} finally {
    # Only the two containers created with this run's unique names are removed.
    $ErrorActionPreference = 'Continue'
    docker rm -f $testSqlName $testPgName 2>$null | Out-Null
    foreach ($testVariable in @('MSSQL_SA_PASSWORD', 'POSTGRES_PASSWORD', 'SQLCMDPASSWORD',
            'INTEREST_TEST_DATABASES', 'INTEREST_TEST_SQL_URL', 'INTEREST_TEST_PG_URL', 'INTEREST_TEST_PASSWORD')) {
        Remove-Item -LiteralPath "Env:$testVariable" -ErrorAction SilentlyContinue
    }
}
