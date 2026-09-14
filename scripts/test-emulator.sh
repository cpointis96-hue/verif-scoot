#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.gradle-home}"
export ANDROID_HOME="${ANDROID_HOME:-$PWD/.tools/android-sdk}"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$PWD/.tools/avd}"
export ANDROID_SERIAL=emulator-5554
mkdir -p artifacts
if ! adb -s "$ANDROID_SERIAL" get-state >/dev/null 2>&1; then
  camera_fixture_dir=$(mktemp -d /tmp/scoot-camera.XXXXXX)
  cp app/src/androidTest/assets/fixture.mp4 "$camera_fixture_dir/fixture.mp4"
  "$ANDROID_HOME/emulator/emulator" -avd scoot_api35 -port 5554 -no-window -no-audio -no-snapshot -gpu swiftshader_indirect -camera-back "videofile:$camera_fixture_dir/fixture.mp4" > artifacts/emulator.log 2>&1 &
fi
python3 - <<'PY'
import subprocess,time
for _ in range(120):
    result=subprocess.run(['adb','-s','emulator-5554','shell','getprop','sys.boot_completed'],capture_output=True,text=True,timeout=5)
    if result.stdout.strip()=='1': break
    time.sleep(1)
else: raise SystemExit('AVD not ready after 120 seconds')
PY
./gradlew assembleDebug assembleDebugAndroidTest
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$ANDROID_SERIAL" shell am instrument -w -r com.scootcheck.app.test/androidx.test.runner.AndroidJUnitRunner > artifacts/device-tests.log
cat artifacts/device-tests.log
python3 - <<'PY'
import pathlib,re
text=pathlib.Path('artifacts/device-tests.log').read_text()
if not re.search(r'OK \(\d+ tests?\)',text):
    raise SystemExit('Android instrumentation failed; see artifacts/device-tests.log')
PY
for name in capture review closed picker export; do
  adb -s "$ANDROID_SERIAL" pull "/sdcard/Android/data/com.scootcheck.app/files/$name.png" "artifacts/$name.png"
done
adb -s "$ANDROID_SERIAL" shell am start -n com.scootcheck.app/.MainActivity
adb -s "$ANDROID_SERIAL" exec-out screencap -p > artifacts/current-screen.png
adb -s "$ANDROID_SERIAL" shell uiautomator dump /sdcard/window.xml
adb -s "$ANDROID_SERIAL" pull /sdcard/window.xml artifacts/window.xml
adb -s "$ANDROID_SERIAL" logcat -d -b crash > artifacts/crash.log
