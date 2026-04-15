param(
    [string]$url = "http://localhost:8080",
    [string]$agent = "linda"
)

$ErrorActionPreference = "Stop"
$projectRoot = (Get-Item $PSScriptRoot).Parent.FullName

Write-Host "Building troupeforge-client..." -ForegroundColor Cyan
& "$projectRoot\gradlew.bat" :troupeforge-client:build -x test --quiet
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed." -ForegroundColor Red
    exit 1
}

$appArgs = "--url $url --agent $agent"

& "$projectRoot\gradlew.bat" :troupeforge-client:run --quiet --console=plain "--args=$appArgs"
