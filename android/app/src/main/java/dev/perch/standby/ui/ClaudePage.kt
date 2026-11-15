package dev.perch.standby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.perch.standby.data.ClaudeState
import dev.perch.standby.data.LinkState
import dev.perch.standby.data.MacSnapshot
import dev.perch.standby.data.SessionDetail
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Claude Code sessions: a list on the left, everything about the selected one on the right. */
@Composable
fun ClaudePage(mac: MacSnapshot, nowSec: Long, use24h: Boolean) {
    val sessions = mac.sessions
    if (sessions.isNullOrEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Claude Code", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        mac.link != LinkState.Connected && mac.link != LinkState.Demo -> linkMessage(mac.link)
                        sessions == null -> "Waiting for session details… (accept the pairing request if one appears)"
                        else -> "No sessions running"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    // Follow the most urgent session unless the user picked one in the last two minutes.
    var pinned by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pinned) {
        if (pinned != null) {
            delay(120_000)
            pinned = null
        }
    }
    val selected = sessions.firstOrNull { it.id == pinned } ?: sessions.first()

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(220.dp).fillMaxHeight()) {
            val waiting = sessions.count { it.state == ClaudeState.Waiting }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                Text("Claude Code", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (waiting > 0) "$waiting need you" else "${sessions.size} active",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (waiting > 0) WaitingColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sessions, key = { it.id }) { s ->
                    SessionRow(s, nowSec, s.id == selected.id) { pinned = s.id }
                }
            }
        }
        SessionDetailPanel(selected, nowSec, use24h, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun SessionRow(s: SessionDetail, nowSec: Long, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(claudeColor(s.state))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    s.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${stateWord(s)} · ${duration(nowSec - s.since)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = claudeColor(s.state),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SessionDetailPanel(s: SessionDetail, nowSec: Long, use24h: Boolean, modifier: Modifier) {
    val waiting = s.state == ClaudeState.Waiting
    Panel(
        modifier,
        color = if (waiting) WaitingColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        // Header: name, path and tags.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(10.dp))
            s.branch?.let { Tag("⎇ $it") }
            s.model?.let { Tag(modelName(it)) }
            s.mode?.takeIf { it != "default" }?.let { Tag(modeName(it), WaitingColor) }
        }
        Text(
            s.cwd,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1.25f).verticalScroll(rememberScrollState())) {
                // What it's doing right now.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(claudeColor(s.state))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        statusLine(s),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (waiting) WaitingColor else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    // For a running tool, time that tool; otherwise time the current state.
                    val from = if (s.tool != null) s.recent.firstOrNull()?.at ?: s.since else s.since
                    Text(
                        duration(nowSec - from),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val sub = if (waiting) s.message else s.detail
                if (sub != null) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 20.dp, top = 2.dp),
                    )
                }
                s.prompt?.let {
                    Spacer(Modifier.height(10.dp))
                    PanelLabel("Last prompt")
                    Text(
                        "“$it”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Stat("Session", duration(nowSec - s.started))
                    Stat("Prompts", s.prompts.toString())
                    Stat("Tool calls", s.tools.toString())
                    Stat("Context", tokens(s.contextTokens))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Stat("Output", tokens(s.outputTokens))
                    Stat("Input", tokens(s.inputTokens + s.cacheWriteTokens))
                    Stat("Cache read", tokens(s.cacheReadTokens))
                    Stat("Last event", duration(nowSec - s.last) + " ago")
                }
            }
            Column(Modifier.weight(1f)) {
                PanelLabel("Recent tools")
                Spacer(Modifier.height(4.dp))
                if (s.recent.isEmpty()) {
                    Text("None yet", style = MaterialTheme.typography.bodyMedium)
                }
                s.recent.forEach { t ->
                    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            clockTime(t.at, use24h),
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(52.dp),
                        )
                        Text(
                            toolName(t.name),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            t.detail ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color = MaterialTheme.colorScheme.secondary) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .padding(end = 6.dp)
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun stateWord(s: SessionDetail) = when (s.state) {
    ClaudeState.Waiting -> "needs you"
    ClaudeState.Working -> s.tool?.let(::toolName) ?: "thinking"
    ClaudeState.Idle -> "idle"
}

private fun statusLine(s: SessionDetail) = when (s.state) {
    ClaudeState.Waiting -> "Needs you"
    ClaudeState.Working -> s.tool?.let { "Running ${toolName(it)}" } ?: "Thinking…"
    ClaudeState.Idle -> "Idle, waiting for a prompt"
}

/** "mcp__github__get_pull_request" -> "github · get_pull_request" */
fun toolName(name: String): String =
    if (name.startsWith("mcp__")) name.removePrefix("mcp__").split("__", limit = 2).joinToString(" · ") else name

/** "claude-opus-5-5" -> "Opus 5.5", "claude-sonnet-4-5-20250929" -> "Sonnet 4.5" */
fun modelName(id: String): String {
    val parts = id.removePrefix("claude-").substringBefore('[').split('-')
    val family = parts.firstOrNull { p -> p.any { it.isLetter() } } ?: return id
    val version = parts.filter { p -> p.length <= 2 && p.all { it.isDigit() } }.joinToString(".")
    return family.replaceFirstChar { it.uppercase() } + if (version.isNotEmpty()) " $version" else ""
}

private fun modeName(mode: String) = when (mode) {
    "acceptEdits" -> "accept edits"
    "bypassPermissions" -> "bypass"
    "plan" -> "plan mode"
    else -> mode
}

fun duration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        else -> String.format(Locale.US, "%dh %02dm", s / 3600, s % 3600 / 60)
    }
}

fun tokens(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1e6)
    n >= 1_000 -> String.format(Locale.US, "%.1fk", n / 1e3).replace(".0k", "k")
    else -> n.toString()
}

private fun clockTime(epochSec: Long, use24h: Boolean): String =
    Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm"))
