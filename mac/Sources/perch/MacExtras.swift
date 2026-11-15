import Foundation
import IOKit.ps

/// Battery state from IOKit power sources; nil on Macs without a battery.
enum Battery {
    static func read() -> [String: Any]? {
        guard let blob = IOPSCopyPowerSourcesInfo()?.takeRetainedValue(),
              let list = IOPSCopyPowerSourcesList(blob)?.takeRetainedValue() as? [CFTypeRef] else { return nil }
        for source in list {
            guard let d = IOPSGetPowerSourceDescription(blob, source)?.takeUnretainedValue() as? [String: Any],
                  d[kIOPSTypeKey] as? String == kIOPSInternalBatteryType,
                  let pct = d[kIOPSCurrentCapacityKey] as? Int else { continue }
            let charging = d[kIOPSIsChargingKey] as? Bool ?? false
            var out: [String: Any] = [
                "pct": pct,
                "chg": charging,
                "ac": d[kIOPSPowerSourceStateKey] as? String == kIOPSACPowerValue,
            ]
            // Minutes until empty (on battery) or full (charging); -1 means still estimating.
            let minutes = (charging ? d[kIOPSTimeToFullChargeKey] : d[kIOPSTimeToEmptyKey]) as? Int ?? -1
            if minutes > 0 { out["min"] = minutes }
            return out
        }
        return nil
    }
}

/// Runs the Now Playing helper inside /usr/bin/perl (the only way MediaRemote still answers
/// on macOS 15.4+) and keeps the latest JSON it printed.
final class MediaWatcher {
    private(set) var current: [String: Any] = [:]
    private var process: Process?
    private var buffer = Data()
    private var restartDelay: TimeInterval = 2

    private static let loader = """
        use DynaLoader;
        my $l = DynaLoader::dl_load_file($ARGV[0], 0) or die "load";
        my $s = DynaLoader::dl_find_symbol($l, "perch_media_stream") or die "symbol";
        DynaLoader::dl_install_xsub("main::run", $s);
        &run();
        """

    func start() {
        guard process == nil,
              let lib = Bundle.main.url(forResource: "libperchmedia", withExtension: "dylib") else { return }
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/usr/bin/perl")
        p.arguments = ["-e", Self.loader, lib.path]
        let pipe = Pipe()
        p.standardOutput = pipe
        p.standardError = FileHandle.nullDevice
        pipe.fileHandleForReading.readabilityHandler = { [weak self] handle in
            let chunk = handle.availableData
            DispatchQueue.main.async { self?.consume(chunk) }
        }
        p.terminationHandler = { [weak self] _ in
            DispatchQueue.main.async {
                guard let self else { return }
                self.process = nil
                self.current = [:]
                DispatchQueue.main.asyncAfter(deadline: .now() + self.restartDelay) { self.start() }
                self.restartDelay = min(self.restartDelay * 2, 300)
            }
        }
        do {
            try p.run()
            process = p
        } catch {
            process = nil
        }
    }

    func stop() {
        process?.terminationHandler = nil
        process?.terminate()
        process = nil
    }

    private func consume(_ chunk: Data) {
        buffer.append(chunk)
        while let newline = buffer.firstIndex(of: 0x0A) {
            let line = buffer[buffer.startIndex..<newline]
            buffer.removeSubrange(buffer.startIndex...newline)
            if let json = try? JSONSerialization.jsonObject(with: line) as? [String: Any], json["error"] == nil {
                current = json
                restartDelay = 2
            }
        }
    }
}
