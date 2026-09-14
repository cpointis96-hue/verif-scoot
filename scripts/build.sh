#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.gradle-home}"
mkdir -p artifacts
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
cp app/build/outputs/apk/debug/app-debug.apk artifacts/verif-scoot-debug.apk
shasum -a 256 artifacts/verif-scoot-debug.apk > artifacts/verif-scoot-debug.apk.sha256
