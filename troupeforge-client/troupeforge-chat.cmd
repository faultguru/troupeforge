@echo off
setlocal

set URL=http://localhost:8080
set AGENT=linda

:parseArgs
if "%~1"=="" goto run
if "%~1"=="--url" (set URL=%~2 & shift & shift & goto parseArgs)
if "%~1"=="-u" (set URL=%~2 & shift & shift & goto parseArgs)
if "%~1"=="--agent" (set AGENT=%~2 & shift & shift & goto parseArgs)
if "%~1"=="-a" (set AGENT=%~2 & shift & shift & goto parseArgs)
shift
goto parseArgs

:run
echo Building troupeforge-client...
call "%~dp0..\gradlew.bat" :troupeforge-client:build -x test --quiet
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

call "%~dp0..\gradlew.bat" :troupeforge-client:run --quiet --console=plain --warning-mode=none "--args=--url %URL% --agent %AGENT%"
