package dev.perch.standby.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID

/**
 * BLE central that finds the Perch Mac agent, subscribes to its characteristics and
 * keeps reconnecting for as long as it is started. All callbacks fire on the main thread.
 */
@SuppressLint("MissingPermission")
class BleLink(
    private val context: Context,
    private val onLink: (LinkState) -> Unit,
    private val onInfo: (String) -> Unit,
    private val onStats: (MacStats) -> Unit,
    private val onClaude: (ClaudeStatus) -> Unit,
    private val onSessions: (List<SessionDetail>) -> Unit,
    private val onExtras: (BatteryInfo?, NowPlaying?) -> Unit,
) {
    /** Reassembles [ver][seq u16][index][count]+slice notifications into one JSON document. */
    private class Assembler {
        private var seq = -1
        private var parts: Array<ByteArray?> = emptyArray()

        fun reset() {
            seq = -1
        }

        fun add(v: ByteArray): String? {
            if (v.size < 5 || v[0].toInt() != 1) return null
            val s = (v[1].toInt() and 0xff) or ((v[2].toInt() and 0xff) shl 8)
            val index = v[3].toInt() and 0xff
            val count = v[4].toInt() and 0xff
            if (count == 0 || index >= count) return null
            if (s != seq || parts.size != count) {
                seq = s
                parts = arrayOfNulls(count)
            }
            parts[index] = v.copyOfRange(5, v.size)
            if (parts.any { it == null }) return null
            seq = -1
            return String(parts.fold(ByteArray(0)) { acc, part -> acc + part!! }, Charsets.UTF_8)
        }
    }

    private val sessionsAssembler = Assembler()
    private val extrasAssembler = Assembler()

    private val main = Handler(Looper.getMainLooper())
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var running = false
    private var scanning = false
    private var gatt: BluetoothGatt? = null
    private val ops = ArrayDeque<(BluetoothGatt) -> Boolean>()

    fun start() {
        if (running) return
        running = true
        scan()
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        stopScan()
        closeGatt()
    }

    private fun hasPermissions() = listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    ).all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun retryLater(delayMs: Long = 4000) {
        main.removeCallbacksAndMessages(null)
        if (running) main.postDelayed(::scan, delayMs)
    }

    private fun scan() {
        if (!running || gatt != null || scanning) return
        if (!hasPermissions()) {
            onLink(LinkState.NoPermission)
            return retryLater()
        }
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            onLink(LinkState.BluetoothOff)
            return retryLater()
        }
        onLink(LinkState.Scanning)
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(Protocol.SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        try {
            scanner.startScan(listOf(filter), settings, scanCallback)
            scanning = true
        } catch (e: RuntimeException) {
            retryLater()
        }
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: RuntimeException) {
        }
    }

    private fun closeGatt() {
        ops.clear()
        try {
            gatt?.close()
        } catch (_: RuntimeException) {
        }
        gatt = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            main.post { connect(result.device) }
        }

        override fun onScanFailed(errorCode: Int) {
            main.post {
                scanning = false
                retryLater(8000)
            }
        }
    }

    private fun connect(device: BluetoothDevice) {
        if (!running || gatt != null) return
        stopScan()
        onLink(LinkState.Connecting)
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        // Give up on a connection attempt that never completes.
        main.postDelayed({ if (running && ops.isEmpty() && !connected) dropAndRescan() }, 15000)
    }

    private var connected = false

    private fun dropAndRescan() {
        connected = false
        closeGatt()
        if (running) {
            onLink(LinkState.Scanning)
            retryLater(2000)
        }
    }

    private fun next(g: BluetoothGatt) {
        while (true) {
            val op = ops.removeFirstOrNull() ?: return
            if (op(g)) return // started; its callback will call next()
        }
    }

    private fun read(uuid: UUID): (BluetoothGatt) -> Boolean = { g ->
        val ch = g.getService(Protocol.SERVICE)?.getCharacteristic(uuid)
        ch != null && g.readCharacteristic(ch)
    }

    private fun subscribe(uuid: UUID): (BluetoothGatt) -> Boolean = { g ->
        val ch = g.getService(Protocol.SERVICE)?.getCharacteristic(uuid)
        val cccd = ch?.getDescriptor(Protocol.CCCD)
        if (ch == null || cccd == null) {
            false
        } else {
            g.setCharacteristicNotification(ch, true)
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                android.bluetooth.BluetoothStatusCodes.SUCCESS
        }
    }

    /**
     * The Mac's Bluetooth daemon can keep the link up after the agent app quits, so a
     * connection that stops delivering stats is treated as dead.
     */
    private val watchdog = Runnable {
        if (running && connected) dropAndRescan()
    }

    private fun kickWatchdog() {
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, 6000)
    }

    private fun dispatch(uuid: UUID, value: ByteArray) {
        when (uuid) {
            Protocol.STATS -> {
                kickWatchdog()
                Protocol.parseStats(value)?.let(onStats)
            }
            Protocol.CLAUDE -> Protocol.parseClaude(value)?.let(onClaude)
            Protocol.INFO -> onInfo(String(value, Charsets.UTF_8))
            Protocol.SESSIONS -> sessionsAssembler.add(value)?.let(Protocol::parseSessions)?.let(onSessions)
            Protocol.EXTRAS -> extrasAssembler.add(value)?.let(Protocol::parseExtras)?.let { (bat, media) ->
                onExtras(bat, media)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (g !== gatt) return@post
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!g.requestMtu(517)) g.discoverServices()
                } else {
                    dropAndRescan()
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            main.post { if (g === gatt) g.discoverServices() }
        }

        override fun onServiceChanged(g: BluetoothGatt) {
            // The agent restarted and re-registered its service: subscribe again.
            main.post { if (g === gatt) g.discoverServices() }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                if (g !== gatt) return@post
                if (status != BluetoothGatt.GATT_SUCCESS || g.getService(Protocol.SERVICE) == null) {
                    return@post dropAndRescan()
                }
                connected = true
                kickWatchdog()
                onLink(LinkState.Connected)
                ops.clear()
                ops += read(Protocol.INFO)
                ops += subscribe(Protocol.STATS)
                ops += subscribe(Protocol.CLAUDE)
                ops += read(Protocol.CLAUDE)
                // Encrypted: the first time, Android pairs with the Mac before this completes.
                ops += subscribe(Protocol.SESSIONS)
                ops += subscribe(Protocol.EXTRAS)
                sessionsAssembler.reset()
                extrasAssembler.reset()
                next(g)
            }
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            main.post {
                if (g !== gatt) return@post
                if (status == BluetoothGatt.GATT_SUCCESS) dispatch(characteristic.uuid, value)
                next(g)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            main.post { if (g === gatt) next(g) }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            main.post { if (g === gatt) dispatch(characteristic.uuid, value) }
        }
    }
}
