package dev.perch.standby.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val HISTORY = 60

/** Live Mac state, fed by Bluetooth or by a simulator when demo mode is on. */
class MacRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val prefs: Prefs,
) {
    private val _state = MutableStateFlow(MacSnapshot())
    val state: StateFlow<MacSnapshot> = _state.asStateFlow()

    private var users = 0
    private var job: Job? = null

    private val ble = BleLink(
        context.applicationContext,
        onLink = { link ->
            _state.update { if (link == LinkState.Connected) it.copy(link = link) else MacSnapshot(link = link) }
        },
        onInfo = { host -> _state.update { it.copy(host = host) } },
        onStats = ::pushStats,
        onClaude = { claude -> _state.update { it.copy(claude = claude) } },
        onSessions = { sessions -> _state.update { it.copy(sessions = sessions) } },
        onExtras = { battery, media -> _state.update { it.copy(battery = battery, media = media) } },
    )

    /** Call while a screen is visible; the link is torn down when the last user releases. */
    fun acquire() {
        if (users++ > 0) return
        job = scope.launch {
            prefs.state.map { it.demoMac }.distinctUntilChanged().collectLatest { demo ->
                _state.value = MacSnapshot()
                if (demo) {
                    runDemo()
                } else {
                    try {
                        ble.start()
                        awaitCancellation()
                    } finally {
                        ble.stop()
                    }
                }
            }
        }
    }

    fun release() {
        if (--users > 0) return
        users = 0
        job?.cancel()
        job = null
    }

    private fun pushStats(stats: MacStats) = _state.update {
        it.copy(
            stats = stats,
            cpuHistory = (it.cpuHistory + stats.cpuPercent.toFloat()).takeLast(HISTORY),
            memHistory = (it.memHistory + stats.memPercent.toFloat()).takeLast(HISTORY),
            downHistory = (it.downHistory + stats.downBytesPerSec.toFloat()).takeLast(HISTORY),
            upHistory = (it.upHistory + stats.upBytesPerSec.toFloat()).takeLast(HISTORY),
        )
    }

    private suspend fun runDemo() {
        val t0 = System.currentTimeMillis() / 1000
        _state.value = MacSnapshot(
            link = LinkState.Demo,
            host = "Demo Mac",
            battery = BatteryInfo(percent = 76, charging = true, onAc = true, minutes = 38),
            media = NowPlaying(
                title = "Weightless", artist = "Marconi Union", app = "Music",
                playing = true, duration = 485, position = 72, at = t0,
            ),
        )
        val frames = listOf(
            ClaudeStatus(
                1, 0, 1,
                listOf(ClaudeSession("perch", ClaudeState.Working), ClaudeSession("dotfiles", ClaudeState.Idle)),
            ),
            ClaudeStatus(
                1, 1, 1,
                listOf(
                    ClaudeSession("api-server", ClaudeState.Waiting),
                    ClaudeSession("perch", ClaudeState.Working),
                    ClaudeSession("dotfiles", ClaudeState.Idle),
                ),
            ),
            ClaudeStatus(
                2, 0, 1,
                listOf(
                    ClaudeSession("api-server", ClaudeState.Working),
                    ClaudeSession("perch", ClaudeState.Working),
                    ClaudeSession("dotfiles", ClaudeState.Idle),
                ),
            ),
        )
        var cpu = 28f
        var mem = 11_200f
        var down = 600_000f
        var up = 90_000f
        var tick = 0
        val started = System.currentTimeMillis() / 1000
        while (true) {
            cpu = (cpu + Random.nextFloat() * 26 - 13).coerceIn(4f, 96f)
            mem = (mem + Random.nextFloat() * 300 - 150).coerceIn(8_000f, 15_000f)
            down = (down * (0.5f + Random.nextFloat() * 1.2f)).coerceIn(20_000f, 40_000_000f)
            up = (up * (0.5f + Random.nextFloat() * 1.2f)).coerceIn(5_000f, 4_000_000f)
            pushStats(
                MacStats(
                    cpuPercent = cpu.toInt(),
                    memUsedMb = mem.toLong(),
                    memTotalMb = 16_384,
                    pressure = 0,
                    downBytesPerSec = down.toLong(),
                    upBytesPerSec = up.toLong(),
                )
            )
            if (tick % 8 == 0) {
                val frame = frames[(tick / 8) % frames.size]
                _state.update { it.copy(claude = frame, sessions = demoSessions(frame, started)) }
            }
            tick++
            delay(1000)
        }
    }

    private fun demoSessions(frame: ClaudeStatus, t0: Long): List<SessionDetail> {
        val now = System.currentTimeMillis() / 1000
        return frame.sessions.mapIndexed { i, s ->
            val tools = listOf(
                ToolUse(now - 4, "Bash", "Run the unit tests"),
                ToolUse(now - 31, "Edit", "BleLink.kt"),
                ToolUse(now - 58, "Read", "MacRepository.kt"),
                ToolUse(now - 95, "Grep", "onServiceChanged"),
                ToolUse(now - 140, "mcp__github__get_pull_request", "#42"),
            )
            SessionDetail(
                id = "demo$i",
                name = s.name,
                cwd = "~/code/${s.name}",
                branch = if (i == 0) "feature/login" else "main",
                model = if (i == 1) "claude-sonnet-5-5" else "claude-opus-5-5",
                mode = if (i == 0) "acceptEdits" else "default",
                state = s.state,
                since = now - 20 - i * 40,
                started = t0 - 1800 - i * 900,
                last = now - 2,
                tool = if (s.state == ClaudeState.Working) "Bash" else null,
                detail = if (s.state == ClaudeState.Working) "Run the unit tests" else null,
                prompt = when (i) {
                    0 -> "The login form accepts empty passwords. Add validation on the server and show an inline error."
                    1 -> "Reconnect automatically when the Mac agent restarts"
                    else -> "Update my zsh aliases for git"
                },
                message = if (s.state == ClaudeState.Waiting) "Claude needs your permission to use Bash" else null,
                prompts = 6 + i * 3,
                tools = 41 + i * 17,
                inputTokens = 1_830,
                outputTokens = 48_200L + i * 9_000,
                cacheReadTokens = 2_400_000,
                cacheWriteTokens = 160_000,
                contextTokens = 86_000L + i * 21_000,
                recent = tools,
            )
        }
    }
}
