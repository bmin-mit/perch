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
        _state.value = MacSnapshot(link = LinkState.Demo, host = "Demo Mac")
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
                _state.update { it.copy(claude = frame) }
            }
            tick++
            delay(1000)
        }
    }
}
