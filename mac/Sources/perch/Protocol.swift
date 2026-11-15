import CoreBluetooth
import Foundation

/// Wire format shared with the Android app (android/.../data/MacModels.kt). Little-endian.
enum PerchProtocol {
    static let service = CBUUID(string: "8f1e0001-6d2a-4b7c-9c41-5065726368aa")
    static let stats = CBUUID(string: "8f1e0002-6d2a-4b7c-9c41-5065726368aa")
    static let claude = CBUUID(string: "8f1e0003-6d2a-4b7c-9c41-5065726368aa")
    static let info = CBUUID(string: "8f1e0004-6d2a-4b7c-9c41-5065726368aa")
    /// Full session details as chunked JSON. Encrypted: subscribing requires pairing.
    static let sessions = CBUUID(string: "8f1e0005-6d2a-4b7c-9c41-5065726368aa")

    /// Session details as JSON, without the "now" stamp (so unchanged data compares equal).
    static func sessionsBody(_ records: [SessionRecord], transcripts: TranscriptReader) -> [[String: Any]] {
        let home = FileManager.default.homeDirectoryForCurrentUser.path
        let sorted = records.sorted {
            ($0.summary.state.priority, -$0.last) < ($1.summary.state.priority, -$1.last)
        }
        return sorted.prefix(8).map { r in
            let t = r.transcript.map { transcripts.totals(for: $0) } ?? TranscriptReader.Totals()
            var d: [String: Any] = [
                "id": String(r.id.prefix(8)), "name": r.name,
                "cwd": r.cwd.hasPrefix(home) ? "~" + r.cwd.dropFirst(home.count) : r.cwd,
                "state": r.state, "since": Int(r.since), "started": Int(r.started), "last": Int(r.last),
                "prompts": r.prompts, "tools": r.tools,
                "in": t.input, "out": t.output, "cr": t.cacheRead, "cw": t.cacheWrite, "ctx": t.context,
                "recent": r.recent.suffix(6).reversed().map { tool -> [String: Any] in
                    var e: [String: Any] = ["t": Int(tool.t), "n": tool.n]
                    if let detail = tool.d { e["d"] = detail }
                    return e
                },
            ]
            let optional: [String: String?] = [
                "branch": t.branch, "model": t.model ?? r.model, "tool": r.tool, "detail": r.detail,
                "prompt": r.prompt, "msg": r.message, "mode": r.mode, "source": r.source,
            ]
            for (key, value) in optional { if let value { d[key] = value } }
            return d
        }
    }

    /// Splits a payload into notifications: [ver=1][seq u16][index][count] + slice.
    static func chunks(_ payload: Data, seq: UInt16, limit: Int) -> [Data] {
        let size = max(limit - 5, 20)
        let count = max(1, (payload.count + size - 1) / size)
        guard count <= 255 else { return [] }
        return (0..<count).map { i in
            var d = Data([1, UInt8(seq & 0xff), UInt8(seq >> 8), UInt8(i), UInt8(count)])
            let start = payload.startIndex + i * size
            d.append(payload[start..<min(start + size, payload.endIndex)])
            return d
        }
    }

    /// [ver=1][cpu%][pressure][reserved] u32 memUsedMb, memTotalMb, downBps, upBps
    static func encode(_ s: Stats) -> Data {
        var d = Data([1, UInt8(clamping: s.cpuPercent), UInt8(clamping: s.pressure), 0])
        for v in [s.memUsedMb, s.memTotalMb, s.downBps, s.upBps] {
            withUnsafeBytes(of: UInt32(clamping: v).littleEndian) { d.append(contentsOf: $0) }
        }
        return d
    }

    /// [ver=1][working][waiting][idle][n] then n x ([state][nameLen][name utf8]).
    /// Kept under `limit` bytes so it fits in one notification at the negotiated MTU.
    static func encode(_ sessions: [ClaudeSession], limit: Int) -> Data {
        let sorted = sessions.sorted { ($0.state.priority, $0.name) < ($1.state.priority, $1.name) }
        let count = { (st: ClaudeSession.State) in UInt8(clamping: sessions.filter { $0.state == st }.count) }
        var d = Data([1, count(.working), count(.waiting), count(.idle), 0])
        var n: UInt8 = 0
        for s in sorted.prefix(8) {
            var name = Data(s.name.utf8.prefix(40))
            // Don't cut a multi-byte character in half.
            while !name.isEmpty, String(data: name, encoding: .utf8) == nil { name.removeLast() }
            if d.count + 2 + name.count > limit { break }
            d.append(s.state.rawValue)
            d.append(UInt8(name.count))
            d.append(name)
            n += 1
        }
        d[4] = n
        return d
    }
}
