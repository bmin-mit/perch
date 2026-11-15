package dev.perch.standby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.perch.standby.data.ClaudeState
import dev.perch.standby.data.LinkState
import dev.perch.standby.data.MacSnapshot

@Composable
fun MacPage(mac: MacSnapshot) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                mac.host ?: "Mac",
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val stats = mac.stats
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChartPanel(
                label = "CPU",
                value = stats?.let { "${it.cpuPercent}%" } ?: "–",
                detail = null,
                history = mac.cpuHistory,
                max = 100f,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            ChartPanel(
                label = "Memory",
                value = stats?.let { "${formatGb(it.memUsedMb)} GB" } ?: "–",
                detail = stats?.let {
                    val pressure = when (it.pressure) {
                        1 -> " · pressure high"
                        2 -> " · pressure critical"
                        else -> ""
                    }
                    "of ${formatGb(it.memTotalMb)} GB$pressure"
                },
                history = mac.memHistory,
                max = 100f,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChartPanel(
                label = "Network",
                value = stats?.let { "↓ ${formatRate(it.downBytesPerSec)}" } ?: "–",
                detail = stats?.let { "↑ ${formatRate(it.upBytesPerSec)}" },
                history = mac.downHistory,
                max = null,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            ClaudePanel(mac, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun ChartPanel(
    label: String,
    value: String,
    detail: String?,
    history: List<Float>,
    max: Float?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Panel(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            PanelLabel(label)
            Spacer(Modifier.weight(1f))
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Sparkline(history, color, Modifier.fillMaxWidth().weight(1f), max)
    }
}

@Composable
private fun ClaudePanel(mac: MacSnapshot, modifier: Modifier = Modifier) {
    val claude = mac.claude
    val waiting = (claude?.waiting ?: 0) > 0
    Panel(
        modifier,
        color = if (waiting) WaitingColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            PanelLabel("Claude")
            Spacer(Modifier.weight(1f))
            if (claude != null && claude.total > 0) {
                Text(
                    "${claude.working} working · ${claude.waiting} waiting · ${claude.idle} idle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        when {
            claude == null -> Text("No data", style = MaterialTheme.typography.titleMedium)
            claude.total == 0 -> Text("No sessions", style = MaterialTheme.typography.titleMedium)
            else -> claude.sessions.take(4).forEach { s ->
                Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(claudeColor(s.state))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        s.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        when (s.state) {
                            ClaudeState.Working -> "working"
                            ClaudeState.Waiting -> "needs you"
                            ClaudeState.Idle -> "idle"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = claudeColor(s.state),
                    )
                }
            }
        }
    }
}
