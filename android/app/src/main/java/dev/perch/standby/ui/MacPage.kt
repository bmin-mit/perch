package dev.perch.standby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.perch.standby.data.LinkState
import dev.perch.standby.data.MacSnapshot

@Composable
fun MacPage(mac: MacSnapshot) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                mac.host ?: "Your Mac",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(12.dp))
            Dot(
                when (mac.link) {
                    LinkState.Connected, LinkState.Demo -> MaterialTheme.colorScheme.primary
                    LinkState.Scanning, LinkState.Connecting -> WaitingColor
                    else -> MaterialTheme.colorScheme.error
                }
            )
            Spacer(Modifier.width(8.dp))
            Text(
                linkMessage(mac.link),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val stats = mac.stats
        if (stats == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Open Perch on your Mac to see CPU, memory and network here.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("CPU", "${stats.cpuPercent}%", "last 60 seconds", Modifier.weight(1f)) {
                TimeChart(
                    series = listOf(Series(mac.cpuHistory, MaterialTheme.colorScheme.primary)),
                    yMax = 100f,
                    yTicks = listOf(0f, 50f, 100f),
                    yLabel = { "${it.toInt()}%" },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Tile(
                label = "Memory",
                value = "${formatGb(stats.memUsedMb)} GB",
                caption = "of ${formatGb(stats.memTotalMb)} GB",
                modifier = Modifier.weight(1f),
            ) {
                // Memory barely moves second to second, so a gauge says more than a flat line.
                val (pressureText, pressureColor) = when (stats.pressure) {
                    2 -> "Pressure critical" to MaterialTheme.colorScheme.error
                    1 -> "Pressure high" to WaitingColor
                    else -> "Pressure normal" to MaterialTheme.colorScheme.tertiary
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RingGauge(
                        fraction = stats.memPercent / 100f,
                        color = pressureColor,
                        track = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${stats.memPercent}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(pressureText, style = MaterialTheme.typography.labelLarge, color = pressureColor)
                    }
                }
            }
            Tile(
                label = "Network",
                value = "↓ ${formatRate(stats.downBytesPerSec)}",
                caption = "↑ ${formatRate(stats.upBytesPerSec)} up",
                captionColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            ) {
                // Both directions share one scale, rounded up to a tidy number.
                val peak = niceCeil((mac.downHistory + mac.upHistory).maxOrNull() ?: 0f, 10_000f)
                TimeChart(
                    series = listOf(
                        Series(mac.downHistory, MaterialTheme.colorScheme.secondary),
                        Series(mac.upHistory, MaterialTheme.colorScheme.tertiary, filled = false),
                    ),
                    yMax = peak,
                    yTicks = listOf(0f, peak / 2, peak),
                    yLabel = { if (it == 0f) "0" else formatRate(it.toLong()).removeSuffix("/s") },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun RingGauge(fraction: Float, color: Color, track: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.padding(4.dp)) {
        val stroke = size.minDimension * 0.09f
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val style = Stroke(width = stroke, cap = StrokeCap.Round)
        // A 270° gauge opening at the bottom, so it reads like a dial.
        drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = style)
        drawArc(color, 135f, 270f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = style)
    }
}

@Composable
private fun Tile(
    label: String,
    value: String,
    caption: String?,
    modifier: Modifier = Modifier,
    captionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Panel(modifier.fillMaxHeight(), color = color) {
        PanelLabel(label)
        Text(
            value,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            caption ?: " ",
            style = MaterialTheme.typography.bodyMedium,
            color = captionColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
    }
}
