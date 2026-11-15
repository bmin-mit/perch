import Darwin
import Foundation

struct Stats {
    var cpuPercent = 0
    var memUsedMb = 0
    var memTotalMb = 0
    /// 0 normal, 1 warning, 2 critical
    var pressure = 0
    var downBps = 0
    var upBps = 0
}

/// Samples CPU, memory and network counters; call `sample()` about once a second.
final class SystemStats {
    private var lastTicks: (busy: UInt64, total: UInt64)?
    private var lastNet: (rx: UInt64, tx: UInt64, at: TimeInterval)?

    func sample() -> Stats {
        var s = Stats()
        s.cpuPercent = cpu()
        (s.memUsedMb, s.memTotalMb) = memory()
        s.pressure = pressure()
        (s.downBps, s.upBps) = network()
        return s
    }

    private func cpu() -> Int {
        var info = host_cpu_load_info()
        var count = mach_msg_type_number_t(MemoryLayout<host_cpu_load_info>.size / MemoryLayout<integer_t>.size)
        let kr = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                host_statistics(mach_host_self(), HOST_CPU_LOAD_INFO, $0, &count)
            }
        }
        guard kr == KERN_SUCCESS else { return 0 }
        let user = UInt64(info.cpu_ticks.0), system = UInt64(info.cpu_ticks.1)
        let idle = UInt64(info.cpu_ticks.2), nice = UInt64(info.cpu_ticks.3)
        let busy = user + system + nice
        let total = busy + idle
        defer { lastTicks = (busy, total) }
        guard let last = lastTicks, total > last.total else { return 0 }
        return Int((Double(busy - last.busy) / Double(total - last.total) * 100).rounded())
    }

    /// Used = app memory + wired + compressed, which matches Activity Monitor's "Memory Used".
    private func memory() -> (Int, Int) {
        let total = Int(ProcessInfo.processInfo.physicalMemory / 1_048_576)
        var stats = vm_statistics64()
        var count = mach_msg_type_number_t(MemoryLayout<vm_statistics64>.size / MemoryLayout<integer_t>.size)
        let kr = withUnsafeMutablePointer(to: &stats) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                host_statistics64(mach_host_self(), HOST_VM_INFO64, $0, &count)
            }
        }
        guard kr == KERN_SUCCESS else { return (0, total) }
        let page = UInt64(vm_kernel_page_size)
        let app = UInt64(stats.internal_page_count) - UInt64(stats.purgeable_count)
        let used = (app + UInt64(stats.wire_count) + UInt64(stats.compressor_page_count)) * page
        return (Int(used / 1_048_576), total)
    }

    private func pressure() -> Int {
        var level: Int32 = 0
        var size = MemoryLayout<Int32>.size
        guard sysctlbyname("kern.memorystatus_vm_pressure_level", &level, &size, nil, 0) == 0 else { return 0 }
        switch level {
        case 4: return 2 // critical
        case 2: return 1 // warning
        default: return 0
        }
    }

    /// Bytes per second across physical interfaces (en*), excluding loopback and VPN tunnels.
    private func network() -> (Int, Int) {
        var rx: UInt64 = 0, tx: UInt64 = 0
        var head: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&head) == 0, let first = head else { return (0, 0) }
        defer { freeifaddrs(head) }
        var p: UnsafeMutablePointer<ifaddrs>? = first
        while let ifa = p {
            defer { p = ifa.pointee.ifa_next }
            guard let addr = ifa.pointee.ifa_addr, addr.pointee.sa_family == UInt8(AF_LINK),
                  let data = ifa.pointee.ifa_data else { continue }
            let name = String(cString: ifa.pointee.ifa_name)
            guard name.hasPrefix("en") else { continue }
            let d = data.assumingMemoryBound(to: if_data.self).pointee
            rx += UInt64(d.ifi_ibytes)
            tx += UInt64(d.ifi_obytes)
        }
        let now = ProcessInfo.processInfo.systemUptime
        defer { lastNet = (rx, tx, now) }
        guard let last = lastNet, now > last.at else { return (0, 0) }
        let dt = now - last.at
        // if_data counters are 32-bit and wrap; treat a wrap as no traffic for that tick.
        let drx = rx >= last.rx ? Double(rx - last.rx) : 0
        let dtx = tx >= last.tx ? Double(tx - last.tx) : 0
        return (Int(drx / dt), Int(dtx / dt))
    }
}
