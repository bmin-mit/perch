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

data class ToolUse(val at: Long, val name: String, val detail: String?)

/** Everything known about one Claude Code session. Times are Unix seconds on the phone's clock. */
data class SessionDetail(
    val id: String,
    val name: String,
    val cwd: String,
    val branch: String?,
    val model: String?,
    val mode: String?,
    val state: ClaudeState,
    val since: Long,
    val started: Long,
    val last: Long,
    val tool: String?,
    val detail: String?,
    val prompt: String?,
    val message: String?,
    val prompts: Int,
    val tools: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long,
    val contextTokens: Long,
    val recent: List<ToolUse>,
)

data class BatteryInfo(val percent: Int, val charging: Boolean, val onAc: Boolean, val minutes: Int?)

/** What the Mac is playing. [at] is when [position] was sampled, on the phone's clock (Unix seconds). */
data class NowPlaying(
    val title: String,
    val artist: String?,
    val app: String?,
    val playing: Boolean,
    val duration: Long?,
    val position: Long?,
    val at: Long,
) {
    fun positionAt(nowSec: Long): Long? = position?.let { p ->
        val moved = if (playing) (nowSec - at).coerceAtLeast(0) else 0
        (p + moved).coerceAtMost(duration ?: Long.MAX_VALUE)
    }
}

data class MacSnapshot(
    val link: LinkState = LinkState.Scanning,
    val host: String? = null,
    val stats: MacStats? = null,
    val claude: ClaudeStatus? = null,
    val battery: BatteryInfo? = null,
    val media: NowPlaying? = null,
    /** Null until the (paired, encrypted) details channel has delivered something. */
    val sessions: List<SessionDetail>? = null,
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
    val SESSIONS: UUID = UUID.fromString("8f1e0005-6d2a-4b7c-9c41-5065726368aa")
    val EXTRAS: UUID = UUID.fromString("8f1e0006-6d2a-4b7c-9c41-5065726368aa")
    val COMMAND: UUID = UUID.fromString("8f1e0007-6d2a-4b7c-9c41-5065726368aa")

    /** Battery and Now Playing JSON: {"now", "bat"?: {...}, "media"?: {...}}. */
    fun parseExtras(json: String): Pair<BatteryInfo?, NowPlaying?>? = try {
        val root = org.json.JSONObject(json)
        val skew = System.currentTimeMillis() / 1000 - root.getLong("now")
        val battery = root.optJSONObject("bat")?.let { b ->
            BatteryInfo(
                percent = b.optInt("pct"),
                charging = b.optBoolean("chg"),
                onAc = b.optBoolean("ac"),
                minutes = if (b.has("min")) b.getInt("min") else null,
            )
        }
        val media = root.optJSONObject("media")?.takeIf { it.has("title") }?.let { m ->
            NowPlaying(
                title = m.getString("title"),
                artist = m.optString("artist").ifEmpty { null },
                app = m.optString("app").ifEmpty { null },
                playing = m.optInt("playing") == 1 || m.optBoolean("playing"),
                duration = if (m.has("dur")) m.getLong("dur") else null,
                position = if (m.has("pos")) m.getLong("pos") else null,
                at = (if (m.has("at")) m.getLong("at") else root.getLong("now")) + skew,
            )
        }
        battery to media
    } catch (e: org.json.JSONException) {
        null
    }

    /** Session details JSON: {"now": macUnixSeconds, "s": [ ... ]}. */
    fun parseSessions(json: String): List<SessionDetail>? = try {
        val root = org.json.JSONObject(json)
        // Shift the Mac's timestamps onto the phone's clock in case the two disagree.
        val skew = System.currentTimeMillis() / 1000 - root.getLong("now")
        val list = root.getJSONArray("s")
        (0 until list.length()).map { i ->
            val o = list.getJSONObject(i)
            fun str(key: String) = if (o.has(key) && !o.isNull(key)) o.getString(key) else null
            val recent = o.optJSONArray("recent")
            SessionDetail(
                id = o.getString("id"),
                name = o.getString("name"),
                cwd = o.optString("cwd"),
                branch = str("branch"),
                model = str("model"),
                mode = str("mode"),
                state = ClaudeState.entries.getOrElse(o.optInt("state")) { ClaudeState.Idle },
                since = o.optLong("since") + skew,
                started = o.optLong("started") + skew,
                last = o.optLong("last") + skew,
                tool = str("tool"),
                detail = str("detail"),
                prompt = str("prompt"),
                message = str("msg"),
                prompts = o.optInt("prompts"),
                tools = o.optInt("tools"),
                inputTokens = o.optLong("in"),
                outputTokens = o.optLong("out"),
                cacheReadTokens = o.optLong("cr"),
                cacheWriteTokens = o.optLong("cw"),
                contextTokens = o.optLong("ctx"),
                recent = (0 until (recent?.length() ?: 0)).map { j ->
                    val r = recent!!.getJSONObject(j)
                    ToolUse(r.optLong("t") + skew, r.getString("n"), if (r.has("d")) r.getString("d") else null)
                },
            )
        }
    } catch (e: org.json.JSONException) {
        null
    }
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
