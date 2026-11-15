package dev.perch.standby.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.perch.standby.data.ClaudeState
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.roundToInt

val WaitingColor = Color(0xFFFFB95C)

@Composable
fun PerchTheme(content: @Composable () -> Unit) {
    // Material You colours from the wallpaper, on a true-black base for OLED.
    val scheme = dynamicDarkColorScheme(LocalContext.current)
        .copy(background = Color.Black, surface = Color.Black)
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Current time, updated on each second boundary. */
@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay(1000 - System.currentTimeMillis() % 1000)
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(28.dp), color = color) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), content = content)
    }
}

@Composable
fun PanelLabel(text: String) {
    Text(
        text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(color, CircleShape))
}

@Composable
fun claudeColor(state: ClaudeState): Color = when (state) {
    ClaudeState.Working -> MaterialTheme.colorScheme.primary
    ClaudeState.Waiting -> WaitingColor
    ClaudeState.Idle -> MaterialTheme.colorScheme.outline
}

/** Filled line chart of recent samples. [max] of null scales to the largest sample. */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier, max: Float? = null) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val top = (max ?: values.max()).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1)
        fun y(v: Float) = size.height - (v / top).coerceIn(0f, 1f) * (size.height - 4f) - 2f
        val line = Path()
        values.forEachIndexed { i, v ->
            if (i == 0) line.moveTo(0f, y(v)) else line.lineTo(i * stepX, y(v))
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 3.dp.toPx(), Offset(size.width, y(values.last())))
    }
}

fun formatRate(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_000_000 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1e6)
    bytesPerSec >= 1_000 -> String.format(Locale.US, "%d KB/s", bytesPerSec / 1000)
    else -> "$bytesPerSec B/s"
}

fun formatGb(mb: Long): String = String.format(Locale.US, "%.1f", mb / 1024.0)

fun formatTemp(celsius: Double, fahrenheit: Boolean): String =
    "${(if (fahrenheit) celsius * 9 / 5 + 32 else celsius).roundToInt()}°"

/** WMO weather code to an emoji glyph and a short description. */
fun weatherGlyph(code: Int, isDay: Boolean): Pair<String, String> = when (code) {
    0 -> (if (isDay) "☀️" else "🌙") to "Clear"
    1 -> (if (isDay) "🌤️" else "🌙") to "Mostly clear"
    2 -> (if (isDay) "⛅" else "☁️") to "Partly cloudy"
    3 -> "☁️" to "Overcast"
    45, 48 -> "🌫️" to "Fog"
    51, 53, 55, 56, 57 -> "🌦️" to "Drizzle"
    61, 63, 65, 66, 67 -> "🌧️" to "Rain"
    71, 73, 75, 77 -> "❄️" to "Snow"
    80, 81, 82 -> "🌧️" to "Showers"
    85, 86 -> "🌨️" to "Snow showers"
    95, 96, 99 -> "⛈️" to "Thunderstorm"
    else -> "☁️" to "Cloudy"
}
