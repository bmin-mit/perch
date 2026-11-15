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
            return
        }
        let service = CBMutableService(type: PerchProtocol.service, primary: true)
        service.characteristics = [statsChar, claudeChar, infoChar]
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
        report()
    }

    func peripheralManagerIsReady(toUpdateSubscribers peripheral: CBPeripheralManager) {
        if pendingClaude { sendClaude() }
    }
}
