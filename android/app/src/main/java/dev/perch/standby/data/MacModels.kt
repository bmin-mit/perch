package dev.perch.standby.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

enum class LinkState { BluetoothOff, NoPermission, Scanning, Connecting, Connected, Demo }

data class MacStats(
    val cpuPercent: Int,
    val memUsedMb: Long,
    val memTotalMb: Long,
    /** 0 normal, 1 warning, 2 critical. */
    val pressure: Int,
    val downBytesPerSec: Long,
    val upBytesPerSec: Long,
) {
    val memPercent: Int get() = if (memTotalMb > 0) (memUsedMb * 100 / memTotalMb).toInt() else 0
}

enum class ClaudeState { Idle, Working, Waiting }

data class ClaudeSession(val name: String, val state: ClaudeState)

data class ClaudeStatus(
    val working: Int,
    val waiting: Int,
    val idle: Int,
    val sessions: List<ClaudeSession>,
) {
    val total: Int get() = working + waiting + idle
}

data class MacSnapshot(
    val link: LinkState = LinkState.Scanning,
    val host: String? = null,
    val stats: MacStats? = null,
    val claude: ClaudeStatus? = null,
    val cpuHistory: List<Float> = emptyList(),
    val memHistory: List<Float> = emptyList(),
    val downHistory: List<Float> = emptyList(),
    val upHistory: List<Float> = emptyList(),
)

/**
 * Wire format shared with the Mac agent (mac/Sources/perch/Protocol.swift).
 * All integers are little-endian.
 */
object Protocol {
    val SERVICE: UUID = UUID.fromString("8f1e0001-6d2a-4b7c-9c41-5065726368aa")
    val STATS: UUID = UUID.fromString("8f1e0002-6d2a-4b7c-9c41-5065726368aa")
    val CLAUDE: UUID = UUID.fromString("8f1e0003-6d2a-4b7c-9c41-5065726368aa")
    val INFO: UUID = UUID.fromString("8f1e0004-6d2a-4b7c-9c41-5065726368aa")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** [ver=1][cpu%][pressure][reserved] u32 memUsedMb, memTotalMb, downBps, upBps */
    fun parseStats(bytes: ByteArray): MacStats? {
        if (bytes.size < 20 || bytes[0].toInt() != 1) return null
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return MacStats(
            cpuPercent = (bytes[1].toInt() and 0xff).coerceIn(0, 100),
            pressure = bytes[2].toInt() and 0xff,
            memUsedMb = b.getInt(4).toLong() and 0xffffffffL,
            memTotalMb = b.getInt(8).toLong() and 0xffffffffL,
            downBytesPerSec = b.getInt(12).toLong() and 0xffffffffL,
            upBytesPerSec = b.getInt(16).toLong() and 0xffffffffL,
        )
    }

    /** [ver=1][working][waiting][idle][n] then n x ([state][nameLen][name utf8]) */
    fun parseClaude(bytes: ByteArray): ClaudeStatus? {
        if (bytes.size < 5 || bytes[0].toInt() != 1) return null
        val sessions = ArrayList<ClaudeSession>()
        var pos = 5
        repeat(bytes[4].toInt() and 0xff) {
            if (pos + 2 > bytes.size) return@repeat
            val state = ClaudeState.entries.getOrElse(bytes[pos].toInt() and 0xff) { ClaudeState.Idle }
            val len = bytes[pos + 1].toInt() and 0xff
            if (pos + 2 + len > bytes.size) return@repeat
            sessions += ClaudeSession(String(bytes, pos + 2, len, Charsets.UTF_8), state)
            pos += 2 + len
        }
        return ClaudeStatus(
            working = bytes[1].toInt() and 0xff,
            waiting = bytes[2].toInt() and 0xff,
            idle = bytes[3].toInt() and 0xff,
            sessions = sessions,
        )
    }
}
