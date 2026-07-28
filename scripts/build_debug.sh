#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
./gradlew clean
./gradlew lintDebug
./gradlew testDebugUnitTest
./gradlew assembleDebug
