#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if rg -n -g '!**/build/**' 'TODO|FIXME|NotImplementedException' app docs README.md SECURITY.md PRIVACY_POLICY.md; then
  echo "Verbotene Platzhalter gefunden." >&2
  exit 1
fi
if rg -n 'READ_CALL_LOG|READ_CONTACTS|READ_PHONE_STATE|READ_SMS' app/src/main/AndroidManifest.xml; then
  echo "Unzulässige Berechtigung im Manifest." >&2
  exit 1
fi
./gradlew lintDebug testDebugUnitTest assembleDebug
