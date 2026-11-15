# Perch

A StandBy-style bedside/desk display for Android 16–17, plus a small Mac companion that streams
CPU, memory, network and Claude Code session status to it over Bluetooth Low Energy.

```
android/   Kotlin + Jetpack Compose (Material 3) app — clock, weather, Mac pages
mac/       Swift menu bar agent — BLE peripheral + `perch hook` for Claude Code
```

## Android

```bash
cd android && ./gradlew :app:installDebug
```

- Swipe left/right between **Clock**, **Weather** and **Mac**; swipe up/down on the clock to change face.
- To show it automatically while charging: ⚙ → *Screen saver settings* → pick **Perch**
  (on Samsung: Settings → Display → Screen saver).
- Weather comes from [Open-Meteo](https://open-meteo.com) (no key) using coarse location, or a city set in ⚙.
- On an emulator the Mac page uses simulated data (toggle *Demo Mac data* in ⚙).

## Mac

```bash
cd mac && ./install-hooks.sh
```

This builds `Perch.app`, copies it to `/Applications`, adds `perch hook` to the Claude Code hooks in
`~/.claude/settings.json` (a backup is written next to it), and starts the app. Allow Bluetooth
access when macOS asks. Add Perch to *System Settings → General → Login Items* to start it at login.

Claude status per session: **working** (prompt submitted / tools running), **needs you**
(a `Notification` hook: permission request or waiting for input), **idle** (stopped).

## Protocol

BLE service `8f1e0001-6d2a-4b7c-9c41-5065726368aa`, Mac = peripheral, phone = central.
Byte layouts live in `mac/Sources/perch/Protocol.swift` and `android/.../data/MacModels.kt`.
