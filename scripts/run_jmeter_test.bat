@echo off
setlocal
set THREADS=%1
if "%THREADS%"=="" set THREADS=20
set LOOPS=%2
if "%LOOPS%"=="" set LOOPS=10
set RAMPUP=%3
if "%RAMPUP%"=="" set RAMPUP=2

echo ======================================================================
echo   PayPink 2.0 - T24 Core Remittance Saga Performance Runner
echo ======================================================================
echo Configuration:
echo   Threads (Users)    : %THREADS%
echo   Loops Per Thread   : %LOOPS%
echo   Ramp-up (seconds)  : %RAMPUP%
echo.

echo Cleaning up previous results...
if exist performance\results rmdir /s /q performance\results
mkdir performance\results

echo Executing JMeter T24 Remittance Saga Test Plan...
jmeter -n -t performance/PayPink_T24_Remittance_Saga.jmx -Jthreads=%THREADS% -Jloops=%LOOPS% -Jrampup=%RAMPUP% -l performance/results/saga_results.jtl -e -o performance/results/report

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Test completed successfully!
    echo Opening HTML Dashboard Report in your browser...
    start performance\results\report\index.html
) else (
    echo JMeter encountered an issue. Check logs above.
)
