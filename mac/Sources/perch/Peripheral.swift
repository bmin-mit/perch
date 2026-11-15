import CoreBluetooth
import Foundation

/// Advertises the Perch service and pushes stats / Claude state to subscribed phones.
final class Peripheral: NSObject, CBPeripheralManagerDelegate {
    private var manager: CBPeripheralManager!
    private let statsChar = CBMutableCharacteristic(
        type: PerchProtocol.stats, properties: [.read, .notify], value: nil, permissions: [.readable])
    private let claudeChar = CBMutableCharacteristic(
        type: PerchProtocol.claude, properties: [.read, .notify], value: nil, permissions: [.readable])
    private let infoChar = CBMutableCharacteristic(
        type: PerchProtocol.info, properties: [.read], value: nil, permissions: [.readable])

    /// Encrypted, chunked JSON channels (subscribing requires a paired phone).
    private let sessionsChannel = JSONChannel(PerchProtocol.sessions)
    private let extrasChannel = JSONChannel(PerchProtocol.extras)
    private var channels: [JSONChannel] { [sessionsChannel, extrasChannel] }

    private var statsValue = Data()
    private var claudeValue = Data()
    private var pendingClaude = false
    private var subscribers: [UUID: CBCentral] = [:]

    /// Called on the main queue whenever the Bluetooth or connection state changes.
    var onStatus: ((String) -> Void)?

    var hostName: String { Host.current().localizedName ?? ProcessInfo.processInfo.hostName }

    override init() {
        super.init()
        manager = CBPeripheralManager(delegate: self, queue: .main)
    }

    /// Smallest notification payload every subscriber can receive in one packet.
    var payloadLimit: Int {
        subscribers.values.map { $0.maximumUpdateValueLength }.min() ?? 180
    }

    func update(stats: Stats) {
        statsValue = PerchProtocol.encode(stats)
        guard !subscribers.isEmpty else { return }
        manager.updateValue(statsValue, for: statsChar, onSubscribedCentrals: nil)
    }

    func update(sessions: [ClaudeSession]) {
        let value = PerchProtocol.encode(sessions, limit: payloadLimit)
        guard value != claudeValue || pendingClaude else { return }
        claudeValue = value
        sendClaude()
    }

    /// Sends full session details when they change (or when `force` is set, e.g. a new subscriber).
    func update(details body: [[String: Any]], force: Bool = false) {
        send(sessionsChannel, fields: ["s": body], force: force)
    }

    /// Sends battery and Now Playing state when it changes.
    func update(extras fields: [String: Any], force: Bool = false) {
        send(extrasChannel, fields: fields, force: force)
    }

    private func send(_ channel: JSONChannel, fields: [String: Any], force: Bool) {
        guard let data = try? JSONSerialization.data(withJSONObject: fields, options: [.sortedKeys]) else { return }
        guard force || data != channel.body else { return }
        channel.body = data
        guard channel.subscribers > 0 else { return }
        // "now" lets the phone shift timestamps onto its own clock.
        var wrapped = fields
        wrapped["now"] = Int(Date().timeIntervalSince1970)
        guard let payload = try? JSONSerialization.data(withJSONObject: wrapped) else { return }
        channel.seq &+= 1
        // Anything still queued is superseded; the phone discards incomplete sequences.
        channel.queue = PerchProtocol.chunks(payload, seq: channel.seq, limit: payloadLimit)
        drain(channel)
    }

    private func drain(_ channel: JSONChannel) {
        while let chunk = channel.queue.first {
            guard manager.updateValue(chunk, for: channel.characteristic, onSubscribedCentrals: nil) else { return }
            channel.queue.removeFirst()
        }
    }

    /// Set by the app so a new subscriber gets the current state immediately.
    var onChannelSubscribed: ((CBUUID) -> Void)?

    private func sendClaude() {
        guard !subscribers.isEmpty else { return }
        // false means the transmit queue is full; retry from peripheralManagerIsReady.
        pendingClaude = !manager.updateValue(claudeValue, for: claudeChar, onSubscribedCentrals: nil)
    }

    private func report() {
        let text: String
        switch manager.state {
        case .poweredOn:
            text = subscribers.isEmpty ? "Waiting for phone…" : "Phone connected"
        case .poweredOff: text = "Bluetooth is off"
        case .unauthorized: text = "Bluetooth access denied"
        case .unsupported: text = "Bluetooth LE unsupported"
        default: text = "Starting Bluetooth…"
        }
        onStatus?(text)
    }

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        report()
        guard peripheral.state == .poweredOn else {
            subscribers.removeAll()
            for channel in channels { channel.reset() }
            return
        }
        let service = CBMutableService(type: PerchProtocol.service, primary: true)
        service.characteristics = [statsChar, claudeChar, infoChar] + channels.map(\.characteristic)
        peripheral.removeAllServices()
        peripheral.add(service)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error {
            onStatus?("Bluetooth error: \(error.localizedDescription)")
            return
        }
        peripheral.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [PerchProtocol.service],
            CBAdvertisementDataLocalNameKey: "Perch",
        ])
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        let value: Data
        switch request.characteristic.uuid {
        case PerchProtocol.stats: value = statsValue
        case PerchProtocol.claude: value = claudeValue
        case PerchProtocol.info: value = Data(hostName.utf8)
        default:
            peripheral.respond(to: request, withResult: .attributeNotFound)
            return
        }
        guard request.offset <= value.count else {
            peripheral.respond(to: request, withResult: .invalidOffset)
            return
        }
        request.value = value.subdata(in: request.offset..<value.count)
        peripheral.respond(to: request, withResult: .success)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral,
                           didSubscribeTo characteristic: CBCharacteristic) {
        subscribers[central.identifier] = central
        report()
        if let channel = channels.first(where: { $0.characteristic.uuid == characteristic.uuid }) {
            channel.subscribers += 1
            onChannelSubscribed?(characteristic.uuid)
        }
        if characteristic.uuid == PerchProtocol.claude {
            // Re-encode for this central's MTU and send right away.
            claudeValue = Data()
            update(sessions: ClaudeSessions.load())
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral,
                           didUnsubscribeFrom characteristic: CBCharacteristic) {
        if characteristic.uuid == PerchProtocol.stats {
            subscribers.removeValue(forKey: central.identifier)
        }
        if let channel = channels.first(where: { $0.characteristic.uuid == characteristic.uuid }) {
            channel.subscribers = max(0, channel.subscribers - 1)
            if channel.subscribers == 0 { channel.queue.removeAll() }
        }
        report()
    }

    func peripheralManagerIsReady(toUpdateSubscribers peripheral: CBPeripheralManager) {
        for channel in channels { drain(channel) }
        if pendingClaude { sendClaude() }
    }
}

/// One encrypted characteristic carrying chunked JSON, plus its send state.
final class JSONChannel {
    let characteristic: CBMutableCharacteristic
    var body = Data()
    var queue: [Data] = []
    var seq: UInt16 = 0
    var subscribers = 0

    init(_ uuid: CBUUID) {
        characteristic = CBMutableCharacteristic(
            type: uuid, properties: [.notifyEncryptionRequired], value: nil, permissions: [.readEncryptionRequired])
    }

    func reset() {
        subscribers = 0
        queue.removeAll()
    }
}
