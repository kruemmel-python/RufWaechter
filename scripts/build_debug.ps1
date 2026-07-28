$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    & .\gradlew.bat clean
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & .\gradlew.bat lintDebug
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & .\gradlew.bat testDebugUnitTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & .\gradlew.bat assembleDebug
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
