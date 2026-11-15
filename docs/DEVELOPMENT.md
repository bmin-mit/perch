# Development setup

How to set up a Mac to work on Perch, build both apps, and run them on a phone. To install a release
build instead, see [INSTALL.md](INSTALL.md).

Perch has two parts that talk over Bluetooth Low Energy:

| Part | Folder | Language | Runs on |
| --- | --- | --- | --- |
| Display app | `android/` | Kotlin, Jetpack Compose, Material 3 | Android 16 or 17 phone |
| Companion agent | `mac/` | Swift (menu bar app) | macOS 14 or later |

## 1. Install the tools

Versions below are what the project is built and tested with. Newer patch releases should work.

| Tool | Version | Install |
| --- | --- | --- |
| Xcode (or Command Line Tools) | Xcode 27.2, Swift 6.4 | App Store, then `xcode-select --install` |
| JDK | 17 or newer (tested with OpenJDK 27) | `brew install openjdk` |
| Android SDK command-line tools | latest | `brew install --cask android-commandlinetools`, or Android Studio |
| Android SDK platform | Android 17 (API 37) | `sdkmanager "platforms;android-37.0"` |
| Android build-tools | 36.0.0 | `sdkmanager "build-tools;36.0.0"` |
| Android platform-tools (`adb`) | 37.x | `sdkmanager "platform-tools"` |
| Emulator (optional) | Android 17 image | `sdkmanager "emulator" "system-images;android-37.0;google_apis_playstore;arm64-v8a"` |
| Gradle | 9.8.2 | No install needed: the project ships the Gradle wrapper (`android/gradlew`) |

Android Studio is optional. Everything here works from the terminal.

### Point the build at your Android SDK

Create `android/local.properties` (git-ignored) with the path to your SDK:

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
```

The SDK lives there when installed by Android Studio. The Homebrew cask installs it under
`/opt/homebrew/share/android-commandlinetools` instead; use that path if you went that way.

Or export `ANDROID_HOME` instead. Put `adb` on your PATH for convenience:

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
```

### Check the setup

```bash
java -version
swift --version
adb version
cd android && ./gradlew --version
```

## 2. Build and run the Mac agent

```bash
cd mac
./build.sh
```

This compiles the agent (`swift build -c release`), compiles the Now Playing helper
(`media/PerchMedia.m`), and assembles an ad-hoc signed `build/Perch.app`. Bluetooth access needs a real
app bundle, so always run the `.app`, not the bare binary.

Run it:

```bash
open build/Perch.app
```

The first launch asks for Bluetooth permission; allow it. A clock icon appears in the menu bar.

To make Claude Code report session status, install the hooks once:

```bash
./install-hooks.sh
```

This copies `Perch.app` to `/Applications`, adds `/Applications/Perch.app/Contents/MacOS/perch hook`
to every Claude Code hook event in `~/.claude/settings.json` (keeping a backup at
`~/.claude/settings.json.perch-backup`), and starts the app. Re-run it only when the hook list changes.

### Rebuilding after a change

```bash
./build.sh
pkill -f /Applications/Perch.app/Contents/MacOS/perch; pkill -f perch_media_stream
rm -rf /Applications/Perch.app && cp -R build/Perch.app /Applications/
open /Applications/Perch.app
```

### Useful while developing

| What | How |
| --- | --- |
| Agent status (Bluetooth, phone connected) | `tail -f ~/.perch/agent.log` |
| Claude session state files | `ls ~/.perch/sessions` |
| Exactly what the phone receives for sessions | `/Applications/Perch.app/Contents/MacOS/perch sessions` |
| Simulate a hook event | `echo '{"session_id":"test-1","hook_event_name":"Notification","cwd":"/tmp/demo"}' \| /Applications/Perch.app/Contents/MacOS/perch hook` (delete `~/.perch/sessions/test-1.json` afterwards) |

## 3. Build and run the Android app

```bash
cd android
./gradlew :app:assembleDebug
```

The APK is at `android/app/build/outputs/apk/debug/app-debug.apk`.

### Prepare a physical phone

1. On the phone, open **Settings → About phone** and tap **Build number** seven times to enable
   Developer options.
2. In **Settings → System → Developer options**, turn on **USB debugging**.
3. Connect the phone by USB and accept the **Allow USB debugging?** prompt on the phone.
4. Check that it shows up as `device` (not `unauthorized`):

   ```bash
   adb devices -l
   ```

### Install and launch

With one phone connected:

```bash
./gradlew :app:installDebug
adb shell am start -n dev.perch.standby/.MainActivity
```

With several devices (for example a phone and an emulator), pick one by serial from `adb devices`:

```bash
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> shell am start -n dev.perch.standby/.MainActivity
```

Use an absolute or correct relative path to the APK. A wrong path fails, and if the output is piped
through `tail` you can miss the error and keep testing the old build.

Add `-g` to `adb install` to grant the Bluetooth and location permissions without the dialogs.

### Pair the phone with the Mac

Battery, Now Playing and Claude session details travel over encrypted characteristics, so the phone must
be bonded with the Mac once:

1. On the Mac, open **System Settings → Bluetooth** and leave it open (the Mac is only discoverable then).
2. On the phone, open **Settings → Connected devices → Pair new device** and pick the Mac.
3. Confirm the code on both devices.

Without pairing, the clock, weather and the basic CPU/RAM and Claude summary still work, but the encrypted
data stays empty.

### Emulator

The emulator cannot reach the Mac over Bluetooth, so the app shows demo data there (Settings →
**Demo Mac data**). Handy controls:

```bash
adb emu geo fix 106.70 10.78        # set the location used for weather
adb emu sensor set light 1          # dark room: night mode on
adb emu sensor set light 300        # bright room: night mode off
```

### Debugging

| What | How |
| --- | --- |
| Bluetooth subscriptions and pairing | `adb logcat -s PerchBle` |
| Crashes | `adb logcat -s AndroidRuntime:E` |
| Screenshot | `adb exec-out screencap -p > screen.png` |
| App settings | `adb shell run-as dev.perch.standby cat shared_prefs/perch.xml` |

The ⚙ button hides 5 seconds after the last touch. To open settings with `adb`, tap the screen and the
⚙ in one command, for example `adb shell input tap 1400 640; sleep 0.7; adb shell input tap <x> <y>`.

## 4. Project conventions

See [CLAUDE.md](../CLAUDE.md) for the code layout, the protocol rules (the BLE protocol is defined in both
`mac/Sources/perch/Protocol.swift` and `android/.../data/MacModels.kt`; change both together), design
rules, and how to add icons with `tools/svg2vector.py`.
