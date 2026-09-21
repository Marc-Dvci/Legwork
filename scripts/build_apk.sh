#!/usr/bin/env bash
# Builds the release APK (signed with the debug key so it installs on any device) into release/.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/android"
./gradlew --no-daemon assembleRelease -q
mkdir -p "$ROOT/release"
cp app/build/outputs/apk/release/app-release.apk "$ROOT/release/legwork.apk"
ls -la "$ROOT/release/legwork.apk"
