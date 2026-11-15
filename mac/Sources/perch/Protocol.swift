import CoreBluetooth
import Foundation

/// Wire format shared with the Android app (android/.../data/MacModels.kt). Little-endian.
enum PerchProtocol {
    static let service = CBUUID(string: "8f1e0001-6d2a-4b7c-9c41-5065726368aa")
    static let stats = CBUUID(string: "8f1e0002-6d2a-4b7c-9c41-5065726368aa")
    static let claude = CBUUID(string: "8f1e0003-6d2a-4b7c-9c41-5065726368aa")
    static let info = CBUUID(string: "8f1e0004-6d2a-4b7c-9c41-5065726368aa")

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
