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

- Swipe left/right between **Clock**, **Weather**, **Mac** and **Claude Code**; swipe up/down on the clock to change face.
- The **Claude Code** page lists sessions (those needing you first) and shows the selected one's
  current tool, waiting reason, last prompt, model, git branch, permission mode, prompt/tool counts,
  token usage, context size and recent tool calls. Tap a session to pin it for two minutes.
- To show it automatically while charging: ⚙ → *Screen saver settings* → pick **Perch**
  (on Samsung: Settings → Display → Screen saver).
- In a dark room the whole display turns dim red (toggle *Dim red in the dark* in ⚙).
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
Session details (`…0005`) are chunked JSON and require an encrypted (paired) link, since they
include prompts and commands; `perch sessions` prints exactly what is sent. Byte layouts live in `mac/Sources/perch/Protocol.swift` and `android/.../data/MacModels.kt`.
