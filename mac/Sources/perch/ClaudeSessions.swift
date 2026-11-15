import Foundation

struct ClaudeSession: Equatable {
    enum State: UInt8 {
        case idle = 0, working = 1, waiting = 2
        /// Sort order for display: sessions that need you first.
        var priority: Int { self == .waiting ? 0 : self == .working ? 1 : 2 }
    }

    var name: String
    var state: State
}

/// Claude Code session state, written by the `perch hook` command (one file per session)
/// and read back by the menu bar app.
enum ClaudeSessions {
    static let directory = FileManager.default.homeDirectoryForCurrentUser
        .appendingPathComponent(".perch/sessions", isDirectory: true)

    /// A session that hasn't reported anything for this long is assumed to be gone
    /// (e.g. the terminal was closed without a SessionEnd hook firing).
    static let staleAfter: TimeInterval = 6 * 3600

    /// Handles one Claude Code hook invocation; the hook payload arrives as JSON on stdin.
    static func handleHook() {
        let input = FileHandle.standardInput.readDataToEndOfFile()
        guard let json = try? JSONSerialization.jsonObject(with: input) as? [String: Any],
              let id = json["session_id"] as? String, !id.isEmpty,
              let event = json["hook_event_name"] as? String else { return }
        let safeId = id.filter { $0.isLetter || $0.isNumber || $0 == "-" }
        let file = directory.appendingPathComponent("\(safeId).json")

        let state: ClaudeSession.State
        switch event {
        case "SessionEnd":
            try? FileManager.default.removeItem(at: file)
            return
        case "UserPromptSubmit", "PreToolUse", "PostToolUse":
            state = .working
        case "Notification":
            // Fired when Claude needs a permission decision or has been waiting for input.
            state = .waiting
        default: // SessionStart, Stop, SubagentStop, …
            state = .idle
        }
        let cwd = json["cwd"] as? String ?? ""
        let name = cwd.isEmpty ? String(safeId.prefix(8)) : URL(fileURLWithPath: cwd).lastPathComponent
        let record: [String: Any] = ["name": name, "state": Int(state.rawValue)]
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        if let data = try? JSONSerialization.data(withJSONObject: record) {
            try? data.write(to: file, options: .atomic)
        }
    }

    static func load() -> [ClaudeSession] {
        let fm = FileManager.default
        guard let files = try? fm.contentsOfDirectory(
            at: directory, includingPropertiesForKeys: [.contentModificationDateKey]
        ) else { return [] }
        let now = Date()
        return files.compactMap { url in
            guard url.pathExtension == "json" else { return nil }
            let modified = (try? url.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate
            if let modified, now.timeIntervalSince(modified) > staleAfter {
                try? fm.removeItem(at: url)
                return nil
            }
            guard let data = try? Data(contentsOf: url),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let name = json["name"] as? String,
                  let raw = json["state"] as? Int,
                  let state = ClaudeSession.State(rawValue: UInt8(clamping: raw)) else { return nil }
            return ClaudeSession(name: name, state: state)
        }
    }
}
