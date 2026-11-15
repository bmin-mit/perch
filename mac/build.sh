#!/bin/bash
# Builds build/Perch.app (a menu bar app; Bluetooth access needs a real app bundle).
set -euo pipefail
cd "$(dirname "$0")"

swift build -c release
APP=build/Perch.app
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS"
cp .build/release/perch "$APP/Contents/MacOS/perch"
cp Info.plist "$APP/Contents/Info.plist"
codesign --force --sign - "$APP"
echo "Built $PWD/$APP"
