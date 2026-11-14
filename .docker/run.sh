#!/bin/sh
# Build the debug APK in Docker and install it on the connected device/emulator.
set -e
cd "$(dirname "$0")/.."

APK=app/build/outputs/apk/debug/app-universal-debug.apk
PACKAGE=app.mihon.dev

# cached, so it only does anything when the Dockerfile has changed
docker build -t mihon-build .docker

# the debug signing key is kept in a volume, so that updates install over each other
docker run --rm -v "$PWD":/src -v mihon-gradle:/root/.gradle -v mihon-android:/root/.android mihon-build \
    ./gradlew --no-daemon assembleDebug

if ! adb install -r "$APK"; then
    # signed with another debug key: reinstall from scratch (wipes the app data)
    adb uninstall "$PACKAGE"
    adb install "$APK"
fi
