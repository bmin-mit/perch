package dev.perch.standby.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import dev.perch.standby.R
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

private fun googleSans(weight: Int) = Font(
    R.font.google_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Google Sans, bundled (a variable font covering 400–700) so it works offline in the screensaver. */
val GoogleSans = FontFamily(googleSans(400), googleSans(500), googleSans(600), googleSans(700))

private val PerchTypography = Typography().let { t ->
    fun TextStyle.sans() = copy(fontFamily = GoogleSans)
    t.copy(
        displayLarge = t.displayLarge.sans(), displayMedium = t.displayMedium.sans(),
        displaySmall = t.displaySmall.sans(), headlineLarge = t.headlineLarge.sans(),
        headlineMedium = t.headlineMedium.sans(), headlineSmall = t.headlineSmall.sans(),
        titleLarge = t.titleLarge.sans(), titleMedium = t.titleMedium.sans(), titleSmall = t.titleSmall.sans(),
        bodyLarge = t.bodyLarge.sans(), bodyMedium = t.bodyMedium.sans(), bodySmall = t.bodySmall.sans(),
        labelLarge = t.labelLarge.sans(), labelMedium = t.labelMedium.sans(), labelSmall = t.labelSmall.sans(),
    )
}

@Composable
fun PerchTheme(content: @Composable () -> Unit) {
    // Material You colours from the wallpaper, on a true-black base for OLED.
    val scheme = dynamicDarkColorScheme(LocalContext.current)
        .copy(background = Color.Black, surface = Color.Black)
    MaterialTheme(colorScheme = scheme, typography = PerchTypography, content = content)
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
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    padding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = color) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** Small caption above a value. 14sp so it still reads from across a desk. */
@Composable
fun PanelLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
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

fun formatRate(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_000_000 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1e6)
    bytesPerSec >= 1_000 -> String.format(Locale.US, "%d KB/s", bytesPerSec / 1000)
    else -> "$bytesPerSec B/s"
}

fun formatGb(mb: Long): String = String.format(Locale.US, "%.1f", mb / 1024.0)

fun formatTemp(celsius: Double, fahrenheit: Boolean): String =
    "${(if (fahrenheit) celsius * 9 / 5 + 32 else celsius).roundToInt()}°"

private enum class Sky { Sun, Moon, Cloud, Rain, Snow, Storm }

private data class Condition(val icon: Int, val label: String, val sky: Sky)

/** WMO weather code to a Lucide line icon and a short description. */
private fun condition(code: Int, isDay: Boolean): Condition = when (code) {
    0 -> if (isDay) Condition(R.drawable.wx_sun, "Clear", Sky.Sun) else Condition(R.drawable.wx_moon, "Clear", Sky.Moon)
    1, 2 -> Condition(
        if (isDay) R.drawable.wx_cloud_sun else R.drawable.wx_cloud_moon,
        if (code == 1) "Mostly clear" else "Partly cloudy",
        if (isDay) Sky.Sun else Sky.Moon,
    )
    3 -> Condition(R.drawable.wx_cloudy, "Overcast", Sky.Cloud)
    45, 48 -> Condition(R.drawable.wx_cloud_fog, "Fog", Sky.Cloud)
    51, 53, 55, 56, 57 -> Condition(R.drawable.wx_cloud_drizzle, "Drizzle", Sky.Rain)
    61, 66 -> Condition(R.drawable.wx_cloud_rain, "Light rain", Sky.Rain)
    63, 65, 67 -> Condition(R.drawable.wx_cloud_rain, "Rain", Sky.Rain)
    71, 85 -> Condition(R.drawable.wx_cloud_snow, "Light snow", Sky.Snow)
    73, 75, 77, 86 -> Condition(R.drawable.wx_snowflake, "Snow", Sky.Snow)
    80, 81, 82 -> Condition(
        if (isDay) R.drawable.wx_cloud_sun_rain else R.drawable.wx_cloud_moon_rain, "Showers", Sky.Rain,
    )
    95, 96, 99 -> Condition(R.drawable.wx_cloud_lightning, "Thunderstorm", Sky.Storm)
    else -> Condition(R.drawable.wx_cloud, "Cloudy", Sky.Cloud)
}

fun weatherLabel(code: Int, isDay: Boolean): String = condition(code, isDay).label

/** A single-weight line icon, softly tinted by the kind of sky. */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val c = condition(code, isDay)
    val tint = when (c.sky) {
        Sky.Sun -> Color(0xFFFFD27A)
        Sky.Moon -> Color(0xFFC9D2FF)
        Sky.Rain -> Color(0xFF94C8FF)
        Sky.Snow -> Color(0xFFE6F0FF)
        Sky.Storm -> Color(0xFFC6AEFF)
        Sky.Cloud -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(painterResource(c.icon), contentDescription = c.label, tint = tint, modifier = modifier.size(size))
}
