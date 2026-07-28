$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $forbidden = rg -n -g "!**/build/**" "TODO|FIXME|NotImplementedException" app docs README.md SECURITY.md PRIVACY_POLICY.md 2>$null
    if ($LASTEXITCODE -eq 0) {
        $forbidden
        throw "Verbotene Platzhalter gefunden."
    }
    rg -n "READ_CALL_LOG|READ_CONTACTS|READ_PHONE_STATE|READ_SMS" app\src\main\AndroidManifest.xml
    if ($LASTEXITCODE -eq 0) { throw "Unzulässige Berechtigung im Manifest." }
    & .\gradlew.bat lintDebug testDebugUnitTest assembleDebug
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
