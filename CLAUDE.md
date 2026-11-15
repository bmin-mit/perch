# Perch

StandBy-style Android display (`android/`) plus a Mac menu bar agent (`mac/`) that streams Mac and
Claude Code status to it over BLE. See README.md for features, the protocol table and tested devices.

## Layout

- `android/app/src/main/java/dev/perch/standby/`
  - `data/` — `BleLink` (BLE central, op queue, chunk reassembly), `MacRepository` (state + demo data),
    `MacModels` (`Protocol` UUIDs and parsers), `WeatherRepository` (Open-Meteo), `Prefs`
  - `ui/` — one file per page (`ClockPage`, `WeatherPage`, `MacPage`, `ClaudePage`), `StandbyScreen`
    (pager, night filter, auto-hiding ⚙), `Components` (theme, Google Sans, `Panel`, weather icons),
    `Charts` (`TimeChart`), `NightMode`, `SettingsOverlay`
  - `StandbyDreamService` — the screensaver; `MainActivity` hosts the same `StandbyScreen`
- `mac/Sources/perch/` — `main.swift` (menu bar app, 1 s tick, `perch hook` / `perch sessions`),
  `Peripheral` (CoreBluetooth, `JSONChannel`), `Protocol`, `SystemStats`, `ClaudeSessions` (hook handler,
  session files, transcript token reader), `MacExtras` (battery, `MediaWatcher`)
- `mac/media/PerchMedia.m` — Now Playing helper dylib, loaded into `/usr/bin/perl` at runtime
- `tools/svg2vector.py` — Lucide SVG → Android vector drawable

## Build and run

```bash
cd android && ./gradlew :app:assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
cd mac && ./build.sh                               # build/Perch.app (also compiles the media dylib)
```

- No Android Studio here: use Gradle from the command line. `adb` is not on PATH; use
  `~/Library/Android/sdk/platform-tools/adb`.
- Reinstall the Mac agent: `pkill -f /Applications/Perch.app/Contents/MacOS/perch; pkill -f perch_media_stream`,
  replace `/Applications/Perch.app` with `mac/build/Perch.app`, then `open` it.
  `mac/install-hooks.sh` also edits `~/.claude/settings.json`; run it only when the hooks need changing.
- Agent status log: `~/.perch/agent.log`. Session state: `~/.perch/sessions/*.json`.
  `/Applications/Perch.app/Contents/MacOS/perch sessions` prints the JSON the phone receives.

## Testing

- Install with `adb -s <serial> install -r <absolute path to APK>`; a relative path from the wrong
  directory fails silently in a pipe and leaves the old build installed.
- The emulator cannot reach the Mac over Bluetooth, so it defaults to demo data (Settings → Demo Mac data).
  Useful emulator controls: `adb emu geo fix <lon> <lat>` for weather, `adb emu sensor set light 1|300`
  to toggle night mode.
- On phones, encrypted channels (`…0005`–`…0007`) need the phone bonded with the Mac. macOS rejected
  pairing started from the app (Pixel log: `BondStateMachine … Status:9 … hciReason:86`) even while
  discoverable; pair from the phone's Bluetooth settings with the Mac's Bluetooth settings open.
  `adb logcat -s PerchBle` shows each subscription's ATT status and bond changes. The Mac's bluetoothd
  log is not readable without admin rights.
- Encrypted data (BAT, Now Playing) can take a few seconds after connecting; wait before judging it missing.
- The clock-face pager sits above the Now Playing line; vertical swipes must start inside the clock area.
  Analog is the second face, so swipe down to get back to digital.
- The ⚙ button hides 5 s after the last touch, so reveal it and tap it in one command
  (`input tap` anywhere, short sleep, `input tap` on ⚙). `uiautomator dump` takes longer than 5 s.
- To test many Claude sessions, write placeholder files into `~/.perch/sessions` (see `SessionRecord` for
  required fields) and delete them afterwards.
- README screenshots come from the emulator's demo data, never from a real device: real captures show
  the Mac name, location and Claude prompts.

## Conventions

- minSdk 36 / targetSdk 37, Kotlin + Compose + Material 3. Dependency versions are pinned in
  `android/app/build.gradle.kts`; pick releases at least two weeks old.
- The protocol is defined twice (Swift and Kotlin). Change both sides together and keep the README
  protocol table in sync. New sensitive data goes on an encrypted chunked-JSON channel.
- Glanceable from a distance: captions ≥ 14sp (`PanelLabel`), values ≥ 22sp, no card fills on the clock
  page, amber (`WaitingColor`) means "Claude needs you". Icons replace text only where unambiguous.
- New icons: download from `unpkg.com/lucide-static@<version>/icons/<name>.svg` into a scratch folder, run
  `python3 -I tools/svg2vector.py <svg dir> android/app/src/main/res/drawable [wx_|ic_]`, and update
  THIRD_PARTY.md (check whether the icon is Feather-derived; those need the MIT notice).
- Commit each change once it is verified on a device; push to GitHub (`origin`, branch `main`) only when
  the user says so. End commit messages with the Co-Authored-By line.
