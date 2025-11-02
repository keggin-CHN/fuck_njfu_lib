#!/usr/bin/env bash
set -euo pipefail

# One-click script to build Android APK and package the repository source.
# Usage:
#   SERVER_URL=http://your-host:5000 BUILD_TYPE=debug ./scripts/package_apk_and_source.sh
# Defaults:
#   SERVER_URL=http://10.0.2.2:5000
#   BUILD_TYPE=debug (debug or release)

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
DIST_DIR="$ROOT_DIR/dist"

SERVER_URL="${SERVER_URL:-http://10.0.2.2:5000}"
BUILD_TYPE="${BUILD_TYPE:-debug}"

mkdir -p "$DIST_DIR"

if [[ ! -d "$ANDROID_DIR" ]]; then
  echo "Android project not found at $ANDROID_DIR" >&2
  exit 1
fi

pushd "$ANDROID_DIR" >/dev/null

# Ensure Gradle wrapper exists
if [[ ! -f ./gradlew ]]; then
  if command -v gradle >/dev/null 2>&1; then
    gradle wrapper
  else
    echo "Gradle wrapper not found and 'gradle' is not installed. Please install Gradle or use Android Studio to generate the wrapper." >&2
    exit 1
  fi
fi

# Validate build type
case "$BUILD_TYPE" in
  debug|release) ;;
  *) echo "BUILD_TYPE must be 'debug' or 'release'" >&2; exit 2 ;;
esac

# Build APK
./gradlew "assemble${BUILD_TYPE^}" -PSERVER_URL="$SERVER_URL"

APK_PATH="app/build/outputs/apk/$BUILD_TYPE/app-$BUILD_TYPE.apk"
if [[ ! -f "$APK_PATH" ]]; then
  echo "APK not found at $APK_PATH" >&2
  exit 3
fi

popd >/dev/null

cp "$ANDROID_DIR/$APK_PATH" "$DIST_DIR/app-$BUILD_TYPE.apk"

# Package source code (tracked files) using git if available, otherwise fallback to zip
TIMESTAMP=$(date +%Y%m%d%H%M%S)
SOURCE_ZIP="$DIST_DIR/source-$TIMESTAMP.zip"

if command -v git >/dev/null 2>&1 && [[ -d "$ROOT_DIR/.git" ]]; then
  git -C "$ROOT_DIR" archive --format=zip HEAD -o "$SOURCE_ZIP"
else
  # Fallback: zip the working tree while excluding heavy/build/hidden dirs
  (
    cd "$ROOT_DIR"
    zip -r "$SOURCE_ZIP" . \
      -x "./dist/*" "./.git/*" "./.venv/*" \
         "./android/.gradle/*" "./android/build/*" "./android/app/build/*" \
         "*.pyc" "*/__pycache__/*"
  )
fi

echo "\nDone. Artifacts:"
echo "- APK:   $DIST_DIR/app-$BUILD_TYPE.apk"
echo "- Source: $SOURCE_ZIP"
