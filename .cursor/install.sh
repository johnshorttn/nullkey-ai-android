#!/usr/bin/env bash
# Idempotent setup for building the NullKey AI Android app in a Cloud Agent.
# Installs the Android SDK (if missing), points Gradle at it, and warms the
# dependency/build cache by assembling the debug APK.
set -euo pipefail

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
CMDLINE_VERSION="11076708"
PLATFORM="platforms;android-34"
BUILD_TOOLS="build-tools;34.0.0"

export ANDROID_SDK_ROOT ANDROID_HOME="$ANDROID_SDK_ROOT"

install_sdk() {
  local sdkmanager="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
  if [ ! -x "$sdkmanager" ]; then
    echo "==> Installing Android command-line tools"
    mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
    tmp="$(mktemp -d)"
    curl -fsSL -o "$tmp/cmdtools.zip" \
      "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VERSION}_latest.zip"
    ( cd "$tmp" && unzip -q cmdtools.zip )
    rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
    mv "$tmp/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
    rm -rf "$tmp"
  fi
  yes | "$sdkmanager" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null 2>&1 || true
  "$sdkmanager" --sdk_root="$ANDROID_SDK_ROOT" \
    "platform-tools" "$PLATFORM" "$BUILD_TOOLS"
}

install_sdk

# Gradle discovers the SDK via local.properties (git-ignored, per-machine).
echo "sdk.dir=$ANDROID_SDK_ROOT" > "$(dirname "$0")/../local.properties"

echo "==> Warming the Gradle build (assembleDebug)"
( cd "$(dirname "$0")/.." && ./gradlew :app:assembleDebug --no-daemon )

echo "==> NullKey AI environment ready"
