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

struct RecentTool: Codable, Equatable {
    /// Unix time the tool started.
    var t: Double
    var n: String
    var d: String?
}

/// Everything the hooks learn about one Claude Code session; stored as JSON, one file per session.
struct SessionRecord: Codable {
    var id: String
    var name: String
    var cwd: String = ""
    var state: Int = 0
    /// When the session entered its current state.
    var since: Double = 0
    var started: Double = 0
    var last: Double = 0
    /// Tool currently running (cleared when it finishes).
    var tool: String?
    var detail: String?
    var prompt: String?
    /// Why the session is waiting, e.g. "Claude needs your permission to use Bash".
    var message: String?
    var mode: String?
    var model: String?
    var source: String?
    var transcript: String?
    var prompts: Int = 0
    var tools: Int = 0
    var recent: [RecentTool] = []

    var summary: ClaudeSession {
        ClaudeSession(name: name, state: ClaudeSession.State(rawValue: UInt8(clamping: state)) ?? .idle)
    }
}

/// Claude Code session state, written by the `perch hook` command and read by the menu bar app.
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
        if event == "SessionEnd" {
            try? FileManager.default.removeItem(at: file)
            return
        }

        let now = Date().timeIntervalSince1970
        let cwd = json["cwd"] as? String ?? ""
        // Name the session after the folder it started in; `cwd` follows later `cd`s.
        var r = (try? Data(contentsOf: file)).flatMap { try? JSONDecoder().decode(SessionRecord.self, from: $0) }
            ?? SessionRecord(
                id: safeId,
                name: cwd.isEmpty ? String(safeId.prefix(8)) : URL(fileURLWithPath: cwd).lastPathComponent,
                cwd: cwd, since: now, started: now)
        r.last = now
        if let mode = json["permission_mode"] as? String { r.mode = mode }
        if let path = json["transcript_path"] as? String { r.transcript = path }

        func set(_ state: ClaudeSession.State) {
            if r.state != Int(state.rawValue) {
                r.state = Int(state.rawValue)
                r.since = now
            }
        }

        switch event {
        case "SessionStart":
            r.source = json["source"] as? String
            if let model = json["model"] as? String { r.model = model }
            r.tool = nil; r.detail = nil; r.message = nil
            set(.idle)
        case "UserPromptSubmit":
            if let prompt = json["prompt"] as? String { r.prompt = clip(prompt, 280) }
            r.prompts += 1
            r.tool = nil; r.detail = nil; r.message = nil
            set(.working)
        case "PreToolUse":
            let name = json["tool_name"] as? String ?? "Tool"
            let detail = describe(name, json["tool_input"] as? [String: Any] ?? [:])
            r.tool = name
            r.detail = detail
            r.tools += 1
            r.recent.append(RecentTool(t: now, n: name, d: detail))
            if r.recent.count > 8 { r.recent.removeFirst(r.recent.count - 8) }
            r.message = nil
            set(.working)
        case "PostToolUse":
            r.tool = nil; r.detail = nil; r.message = nil
            set(.working)
        case "Notification":
            let message = json["message"] as? String
            let type = json["notification_type"] as? String
            // An "idle" notification just means Claude finished and is waiting for a new prompt.
            if type == "idle_prompt" || (message?.contains("waiting for your input") ?? false) {
                r.message = nil
                set(.idle)
            } else {
                r.message = message.map { clip($0, 160) }
                set(.waiting)
            }
        case "Stop":
            r.tool = nil; r.detail = nil; r.message = nil
            set(.idle)
        default:
            break
        }

        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(r) {
            try? data.write(to: file, options: .atomic)
        }
    }

    /// A short, single-line description of what a tool call is doing.
    static func describe(_ tool: String, _ input: [String: Any]) -> String? {
        func s(_ key: String) -> String? {
            guard let v = input[key] as? String, !v.isEmpty else { return nil }
            return v
        }
        let raw: String?
        switch tool {
        case "Bash": raw = s("description") ?? s("command")
        case "Read", "Edit", "Write", "MultiEdit", "NotebookEdit":
            raw = (s("file_path") ?? s("notebook_path")).map { URL(fileURLWithPath: $0).lastPathComponent }
        case "Grep", "Glob": raw = s("pattern")
        case "WebFetch": raw = s("url").flatMap { URL(string: $0)?.host } ?? s("url")
        case "WebSearch": raw = s("query")
        case "Task", "Agent": raw = s("description")
        default: raw = input.keys.sorted().lazy.compactMap { s($0) }.first
        }
        return raw.map { clip($0, 90) }
    }

    static func clip(_ text: String, _ limit: Int) -> String {
        let flat = text.split(whereSeparator: \.isNewline).joined(separator: " ")
            .trimmingCharacters(in: .whitespaces)
        return flat.count > limit ? String(flat.prefix(limit - 1)) + "…" : flat
    }

    static func loadRecords() -> [SessionRecord] {
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
            guard let data = try? Data(contentsOf: url) else { return nil }
            return try? JSONDecoder().decode(SessionRecord.self, from: data)
        }
    }

    static func load() -> [ClaudeSession] { loadRecords().map(\.summary) }
}

/// Follows Claude Code transcripts (JSONL) incrementally to pick up the model, git branch and token usage.
final class TranscriptReader {
    struct Totals {
        var model: String?
        var branch: String?
        var input = 0, output = 0, cacheRead = 0, cacheWrite = 0
        /// Tokens in the context window as of the latest reply.
        var context = 0
        fileprivate var offset: UInt64 = 0
        fileprivate var lastId: String?
        fileprivate var lastUsage = [0, 0, 0, 0]
    }

    private var cache: [String: Totals] = [:]

    func totals(for path: String) -> Totals {
        var t = cache[path] ?? Totals()
        guard let handle = FileHandle(forReadingAtPath: path) else { return t }
        defer { try? handle.close() }
        let size = (try? handle.seekToEnd()) ?? 0
        if size < t.offset { t = Totals() } // file was replaced
        if size > t.offset {
            try? handle.seek(toOffset: t.offset)
            let data = handle.readData(ofLength: Int(min(size - t.offset, 64 << 20)))
            // Only consume complete lines; a partially written line is read next time.
            if let end = data.lastIndex(of: 0x0A) {
                let chunk = data[data.startIndex...end]
                for line in chunk.split(separator: 0x0A) { process(Data(line), &t) }
                t.offset += UInt64(chunk.count)
            }
        }
        cache[path] = t
        return t
    }

    func forget(except paths: Set<String>) {
        cache = cache.filter { paths.contains($0.key) }
    }

    private func process(_ line: Data, _ t: inout Totals) {
        guard let obj = try? JSONSerialization.jsonObject(with: line) as? [String: Any] else { return }
        if let branch = obj["gitBranch"] as? String, !branch.isEmpty { t.branch = branch }
        guard obj["type"] as? String == "assistant",
              let msg = obj["message"] as? [String: Any],
              let usage = msg["usage"] as? [String: Any] else { return }
        if let model = msg["model"] as? String, !model.hasPrefix("<") { t.model = model }
        let u = ["input_tokens", "output_tokens", "cache_read_input_tokens", "cache_creation_input_tokens"]
            .map { usage[$0] as? Int ?? 0 }
        // One reply is logged as several lines (one per content block) sharing an id and usage.
        let id = msg["id"] as? String
        if id != nil, id == t.lastId {
            t.input -= t.lastUsage[0]; t.output -= t.lastUsage[1]
            t.cacheRead -= t.lastUsage[2]; t.cacheWrite -= t.lastUsage[3]
        }
        t.input += u[0]; t.output += u[1]; t.cacheRead += u[2]; t.cacheWrite += u[3]
        t.lastId = id
        t.lastUsage = u
        if obj["isSidechain"] as? Bool != true { t.context = u[0] + u[2] + u[3] }
    }
}
