# PayPink 2.0 Integration Test Runner PowerShell Wrapper
$scriptPath = Join-Path $PSScriptRoot "run_integration_tests.mjs"
node $scriptPath
exit $LASTEXITCODE
