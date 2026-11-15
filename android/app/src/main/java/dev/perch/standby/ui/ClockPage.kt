package dev.perch.standby.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        // Swipe up/down to switch clock faces, like StandBy.
        val faces = rememberPagerState(initialPage = settings.clockFace.coerceIn(0, FACES - 1)) { FACES }
        LaunchedEffect(faces.settledPage) { onFaceChange(faces.settledPage) }
        VerticalPager(faces, Modifier.weight(1.8f).fillMaxHeight()) { face ->
            when (face) {
                0 -> DigitalFace(now, settings.use24h)
                else -> AnalogFace(now)
            }
        }
        Spacer(Modifier.width(24.dp))
        // Glance stack: no card backgrounds, so the clock stays the hero (StandBy removes widget backgrounds).
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            WeatherGlance(weather, settings.fahrenheit)
            GlanceDivider()
            MacGlance(mac)
            GlanceDivider()
            ClaudeGlance(mac)
        }
    }
}

@Composable
private fun GlanceDivider() {
    HorizontalDivider(
        Modifier.padding(vertical = 14.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun DigitalFace(now: LocalDateTime, use24h: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // "00:00" in a bold face is roughly 2.7 em wide; size it to fill the box.
        val size = with(LocalDensity.current) { minOf(maxWidth / 2.75f, maxHeight * 0.62f).toSp() }
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
private fun WeatherGlance(state: WeatherState, fahrenheit: Boolean) {
    if (state !is WeatherState.Ready) {
        PanelLabel("Weather")
        Text(weatherMessage(state), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
        return
    }
    val w = state.weather
    val label = weatherLabel(w.code, w.isDay)
    Row(verticalAlignment = Alignment.CenterVertically) {
        WeatherIcon(w.code, w.isDay, 40.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            formatTemp(w.tempC, fahrenheit),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "H ${formatTemp(w.highC, fahrenheit)}  L ${formatTemp(w.lowC, fahrenheit)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun weatherMessage(state: WeatherState): String = when (state) {
    WeatherState.Loading -> "Loading weather…"
    WeatherState.NeedsLocation -> "Allow location, or set a city in Settings"
    is WeatherState.Failed -> state.message
    is WeatherState.Ready -> ""
}

fun linkMessage(link: LinkState): String = when (link) {
    LinkState.BluetoothOff -> "Turn on Bluetooth to see your Mac"
    LinkState.NoPermission -> "Allow Bluetooth in Settings"
    LinkState.Scanning -> "Looking for your Mac…"
    LinkState.Connecting -> "Connecting…"
    LinkState.Connected -> "Connected"
    LinkState.Demo -> "Demo data"
}

@Composable
private fun MacGlance(mac: MacSnapshot) {
    val stats = mac.stats
    if (stats == null) {
        PanelLabel("Mac")
        Text(linkMessage(mac.link), style = MaterialTheme.typography.bodyLarge)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Meter("CPU", stats.cpuPercent, Modifier.weight(1f))
        Meter("Memory", stats.memPercent, Modifier.weight(1f))
    }
}

@Composable
private fun Meter(label: String, percent: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            PanelLabel(label)
            Spacer(Modifier.weight(1f))
            Text("$percent%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun ClaudeGlance(mac: MacSnapshot) {
    val claude = mac.claude
    val (state, text) = when {
        claude == null -> ClaudeState.Idle to if (mac.stats == null) "Not connected" else "No status yet"
        claude.waiting > 0 -> ClaudeState.Waiting to
            if (claude.waiting == 1) "1 session needs you" else "${claude.waiting} sessions need you"
        claude.working > 0 -> ClaudeState.Working to
            if (claude.working == 1) "1 session working" else "${claude.working} sessions working"
        claude.total > 0 -> ClaudeState.Idle to "${claude.total} idle"
        else -> ClaudeState.Idle to "No sessions"
    }
    // The one colored fill on this page: only when Claude is waiting on you.
    val waiting = state == ClaudeState.Waiting
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (waiting) Modifier.background(WaitingColor.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                else Modifier
            )
            .padding(horizontal = if (waiting) 12.dp else 0.dp, vertical = if (waiting) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            PanelLabel("Claude Code", if (waiting) WaitingColor else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(claudeColor(state))
                Spacer(Modifier.width(10.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
