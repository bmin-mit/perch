package dev.perch.standby.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.perch.standby.data.ClaudeState
import dev.perch.standby.data.LinkState
import dev.perch.standby.data.MacSnapshot
import dev.perch.standby.data.Settings
import dev.perch.standby.data.WeatherState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val FACES = 2
private val DateFormat = DateTimeFormatter.ofPattern("EEEE, MMM d")

@Composable
fun ClockPage(
    now: LocalDateTime,
    settings: Settings,
    weather: WeatherState,
    mac: MacSnapshot,
    onFaceChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // Swipe up/down to switch clock faces, like StandBy.
        val faces = rememberPagerState(initialPage = settings.clockFace.coerceIn(0, FACES - 1)) { FACES }
        LaunchedEffect(faces.settledPage) { onFaceChange(faces.settledPage) }
        VerticalPager(faces, Modifier.weight(1.7f).fillMaxHeight()) { face ->
            when (face) {
                0 -> DigitalFace(now, settings.use24h)
                else -> AnalogFace(now)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            WeatherSummary(weather, settings.fahrenheit)
            MacSummary(mac)
            ClaudeSummary(mac)
        }
    }
}

@Composable
private fun DigitalFace(now: LocalDateTime, use24h: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // "00:00" in a bold face is roughly 2.7 em wide; size it to fill the box.
        val size = with(LocalDensity.current) { minOf(maxWidth / 2.8f, maxHeight * 0.62f).toSp() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm")),
                    fontSize = size,
                    lineHeight = size,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.03).sp * size.value,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                )
                if (!use24h) {
                    Text(
                        now.format(DateTimeFormatter.ofPattern("a")),
                        Modifier.padding(start = 8.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                now.format(DateFormat),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AnalogFace(now: LocalDateTime) {
    val tick = MaterialTheme.colorScheme.outline
    val major = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
    ) {
        Canvas(Modifier.fillMaxHeight(0.92f).aspectRatio(1f)) {
            val r = size.minDimension / 2
            val c = center
            for (i in 0 until 60) {
                val big = i % 5 == 0
                rotate(i * 6f, c) {
                    drawLine(
                        if (big) major else tick,
                        Offset(c.x, c.y - r),
                        Offset(c.x, c.y - r + r * if (big) 0.11f else 0.045f),
                        strokeWidth = r * if (big) 0.022f else 0.01f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            val minute = now.minute + now.second / 60f
            val hour = now.hour % 12 + minute / 60f
            rotate(hour * 30f, c) {
                drawLine(major, c, Offset(c.x, c.y - r * 0.5f), r * 0.07f, StrokeCap.Round)
            }
            rotate(minute * 6f, c) {
                drawLine(major, c, Offset(c.x, c.y - r * 0.78f), r * 0.045f, StrokeCap.Round)
            }
            rotate(now.second * 6f, c) {
                drawLine(accent, Offset(c.x, c.y + r * 0.12f), Offset(c.x, c.y - r * 0.84f), r * 0.015f, StrokeCap.Round)
            }
            drawCircle(accent, r * 0.04f, c)
        }
        Column {
            Text(
                now.format(DateTimeFormatter.ofPattern("EEE")).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                now.dayOfMonth.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                now.format(DateTimeFormatter.ofPattern("MMMM")),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeatherSummary(state: WeatherState, fahrenheit: Boolean) {
    Panel(Modifier.fillMaxWidth()) {
        when (state) {
            is WeatherState.Ready -> {
                val w = state.weather
                val (glyph, label) = weatherGlyph(w.code, w.isDay)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(glyph, fontSize = 30.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        formatTemp(w.tempC, fahrenheit),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "H ${formatTemp(w.highC, fahrenheit)}  L ${formatTemp(w.lowC, fahrenheit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            else -> {
                PanelLabel("Weather")
                Text(weatherMessage(state), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

fun weatherMessage(state: WeatherState): String = when (state) {
    WeatherState.Loading -> "Loading…"
    WeatherState.NeedsLocation -> "Allow location or set a city in settings"
    is WeatherState.Failed -> state.message
    is WeatherState.Ready -> ""
}

fun linkMessage(link: LinkState): String = when (link) {
    LinkState.BluetoothOff -> "Bluetooth is off"
    LinkState.NoPermission -> "Bluetooth permission needed"
    LinkState.Scanning -> "Searching for Mac…"
    LinkState.Connecting -> "Connecting…"
    LinkState.Connected -> "Connected"
    LinkState.Demo -> "Demo data"
}

@Composable
private fun MacSummary(mac: MacSnapshot) {
    Panel(Modifier.fillMaxWidth()) {
        val stats = mac.stats
        if (stats == null) {
            PanelLabel("Mac")
            Text(linkMessage(mac.link), style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Meter("CPU", stats.cpuPercent, Modifier.weight(1f))
                Meter("RAM", stats.memPercent, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Meter(label: String, percent: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            PanelLabel(label)
            Spacer(Modifier.weight(1f))
            Text("$percent%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun ClaudeSummary(mac: MacSnapshot) {
    val claude = mac.claude
    val waiting = (claude?.waiting ?: 0) > 0
    Panel(
        Modifier.fillMaxWidth(),
        color = if (waiting) WaitingColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        PanelLabel("Claude")
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (state, text) = when {
                claude == null -> ClaudeState.Idle to "No data"
                claude.waiting > 0 -> ClaudeState.Waiting to
                    if (claude.waiting == 1) "1 session needs you" else "${claude.waiting} sessions need you"
                claude.working > 0 -> ClaudeState.Working to "${claude.working} working"
                claude.total > 0 -> ClaudeState.Idle to "${claude.total} idle"
                else -> ClaudeState.Idle to "No sessions"
            }
            Dot(claudeColor(state))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
