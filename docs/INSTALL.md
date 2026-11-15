# Installing a release build

How to build release versions of both apps and install them on a Mac and on an Android phone over USB
with `adb`. For a development setup, see [DEVELOPMENT.md](DEVELOPMENT.md); the tools listed there are
needed here too.

## Mac

### Build

```bash
cd mac
./build.sh
```

The result is `mac/build/Perch.app`, ad-hoc signed (no Apple Developer account needed).

### Install on this Mac

```bash
./install-hooks.sh
```

This replaces `/Applications/Perch.app`, adds the Claude Code hooks to `~/.claude/settings.json` (backup
at `~/.claude/settings.json.perch-backup`) and starts the app. Allow Bluetooth when macOS asks.

To update an existing install without touching the hooks:

```bash
pkill -f /Applications/Perch.app/Contents/MacOS/perch; pkill -f perch_media_stream
rm -rf /Applications/Perch.app && cp -R build/Perch.app /Applications/
open /Applications/Perch.app
```

To start Perch at login, add it in **System Settings → General → Login Items**.

### Install on another Mac

Zip the app with `ditto`, which keeps the bundle intact:

```bash
ditto -c -k --keepParent build/Perch.app build/Perch.zip
```

Copy `Perch.zip` to the other Mac and unzip it into `/Applications`. Because the app is ad-hoc signed,
Gatekeeper blocks it the first time: open it once from Finder with right-click → **Open**, or clear the
download flag:

```bash
xattr -dr com.apple.quarantine /Applications/Perch.app
```

Then register the Claude Code hooks on that Mac. `install-hooks.sh` expects a repository checkout (it
builds the app if `mac/build/Perch.app` is missing), so either clone the repo there and run
`mac/install-hooks.sh`, or add the hook by hand to `~/.claude/settings.json`, once per event
(`SessionStart`, `UserPromptSubmit`, `PreToolUse`, `PostToolUse`, `Notification`, `Stop`, `SessionEnd`):

```json
{
  "hooks": {
    "Notification": [
      { "hooks": [{ "type": "command", "command": "/Applications/Perch.app/Contents/MacOS/perch hook" }] }
    ]
  }
}
```

### Uninstall

```bash
pkill -f /Applications/Perch.app/Contents/MacOS/perch; pkill -f perch_media_stream
rm -rf /Applications/Perch.app ~/.perch
cp ~/.claude/settings.json.perch-backup ~/.claude/settings.json   # restores the hooks as they were before install
```

## Android

### 1. Create a signing key (once)

Release builds should be signed with your own key. Keep the key file and its passwords safe and out of
git: an update can only be installed over a build signed with the same key.

```bash
keytool -genkeypair -v -keystore ~/perch-release.jks -alias perch \
    -keyalg RSA -keysize 2048 -validity 10000
```

Then create `android/keystore.properties` (git-ignored):

```properties
storeFile=/Users/<you>/perch-release.jks
storePassword=<the keystore password>
keyAlias=perch
keyPassword=<the key password>
```

If this file is missing, the release build is signed with the local debug key instead. It still builds and
installs, but only on the machine that built it and only over other builds signed with that debug key.

### 2. Build the release APK

```bash
cd android
./gradlew :app:assembleRelease
```

The APK is at `android/app/build/outputs/apk/release/app-release.apk`. Check which key signed it:

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

### 3. Prepare the phone

The phone must run Android 16 or 17.

1. **Settings → About phone** → tap **Build number** seven times to enable Developer options.
2. **Settings → System → Developer options** → turn on **USB debugging**.
3. Connect by USB and accept **Allow USB debugging?** on the phone.
4. Confirm it is listed as `device`:

   ```bash
   adb devices -l
   ```

### 4. Install

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

With more than one device connected, add `-s <serial>` (from `adb devices`) after `adb`. Add `-g` to grant
the Bluetooth and location permissions up front instead of answering the dialogs.

If a build signed with a different key is already installed (for example a debug build from development),
the install fails with:

```
Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package dev.perch.standby signatures do not match newer version; ignoring!]
```

Uninstall the old build first. This also clears the app's settings (weather city, night colour and so on):

```bash
adb uninstall dev.perch.standby
adb install app/build/outputs/apk/release/app-release.apk
```

Launch it:

```bash
adb shell am start -n dev.perch.standby/.MainActivity
```

### 5. Set up on the phone

1. Allow the Bluetooth and location permissions if asked.
2. Pair the phone with the Mac once: on the Mac open **System Settings → Bluetooth** and leave it open,
   then on the phone go to **Settings → Connected devices → Pair new device**, pick the Mac and confirm the
   code on both. Battery, Now Playing and Claude session details need this.
3. To show Perch automatically while charging: tap the screen, tap ⚙ → **Screen saver settings**, and pick
   **Perch** (on Samsung: Settings → Display → Screen saver).

### Uninstall

```bash
adb uninstall dev.perch.standby
```

## Release checklist

1. Bump `versionCode` and `versionName` in `android/app/build.gradle.kts`, and `CFBundleShortVersionString`
   and `CFBundleVersion` in `mac/Info.plist`.
2. Build both: `mac/build.sh` and `./gradlew :app:assembleRelease`.
3. Install on a phone and the Mac as above, pair, and check every page: clock (with BAT and Now Playing),
   weather, Mac graphs, Claude Code (including removing a session).
4. Update the tested devices table in the README.
