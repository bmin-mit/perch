import AppKit

// `perch hook` is what Claude Code runs on each hook event; anything else starts the menu bar app.
if CommandLine.arguments.dropFirst().first == "hook" {
    ClaudeSessions.handleHook()
    exit(0)
}
// `perch sessions` prints the session details exactly as they are sent to the phone.
if CommandLine.arguments.dropFirst().first == "sessions" {
    let body = PerchProtocol.sessionsBody(ClaudeSessions.loadRecords(), transcripts: TranscriptReader())
    if let data = try? JSONSerialization.data(withJSONObject: body, options: [.prettyPrinted, .sortedKeys]) {
        print(String(decoding: data, as: UTF8.self))
    }
    exit(0)
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    private let statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
    private let peripheral = Peripheral()
    private let system = SystemStats()
    private let statusLine = NSMenuItem(title: "Starting…", action: nil, keyEquivalent: "")
    private let statsLine = NSMenuItem(title: "", action: nil, keyEquivalent: "")
    private let claudeLine = NSMenuItem(title: "", action: nil, keyEquivalent: "")
    private var timer: Timer?
    private let transcripts = TranscriptReader()

    func applicationDidFinishLaunching(_ notification: Notification) {
        statusItem.button?.image = NSImage(systemSymbolName: "clock.badge", accessibilityDescription: "Perch")
        let menu = NSMenu()
        for item in [statusLine, statsLine, claudeLine] {
            item.isEnabled = false
            menu.addItem(item)
        }
        menu.addItem(.separator())
        menu.addItem(NSMenuItem(title: "Quit Perch", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))
        statusItem.menu = menu

        peripheral.onStatus = { [weak self] text in
            self?.statusLine.title = text
            // Also log it, so the state is visible without opening the menu.
            let line = "\(ISO8601DateFormatter().string(from: Date())) \(text)\n"
            let log = ClaudeSessions.directory.deletingLastPathComponent().appendingPathComponent("agent.log")
            try? FileManager.default.createDirectory(
                at: log.deletingLastPathComponent(), withIntermediateDirectories: true)
            if let handle = try? FileHandle(forWritingTo: log) {
                handle.seekToEndOfFile()
                handle.write(Data(line.utf8))
                try? handle.close()
            } else {
                try? Data(line.utf8).write(to: log)
            }
        }
        peripheral.onSessionsSubscribed = { [weak self] in
            guard let self else { return }
            let records = ClaudeSessions.loadRecords()
            self.peripheral.update(
                details: PerchProtocol.sessionsBody(records, transcripts: self.transcripts), force: true)
        }
        _ = system.sample() // prime the counters so the first real sample has a baseline
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.tick() }
        timer?.tolerance = 0.1
    }

    private func tick() {
        let s = system.sample()
        peripheral.update(stats: s)
        let records = ClaudeSessions.loadRecords()
        let sessions = records.map(\.summary)
        peripheral.update(sessions: sessions)
        peripheral.update(details: PerchProtocol.sessionsBody(records, transcripts: transcripts))
        transcripts.forget(except: Set(records.compactMap(\.transcript)))

        statsLine.title = String(
            format: "CPU %d%% · RAM %.1f/%.0f GB · ↓%@ ↑%@",
            s.cpuPercent, Double(s.memUsedMb) / 1024, Double(s.memTotalMb) / 1024,
            rate(s.downBps), rate(s.upBps))
        let waiting = sessions.filter { $0.state == .waiting }.count
        let working = sessions.filter { $0.state == .working }.count
        claudeLine.title = sessions.isEmpty
            ? "Claude: no sessions"
            : "Claude: \(working) working · \(waiting) waiting · \(sessions.count - working - waiting) idle"
    }

    private func rate(_ bps: Int) -> String {
        bps >= 1_000_000 ? String(format: "%.1f MB/s", Double(bps) / 1e6)
            : bps >= 1_000 ? "\(bps / 1000) KB/s" : "\(bps) B/s"
    }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()
