package dev.perch.standby.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.perch.standby.R
import dev.perch.standby.data.Settings
import dev.perch.standby.data.Weather
import dev.perch.standby.data.WeatherState
import java.util.Locale
import kotlin.math.roundToInt

private val RainBlue = Color(0xFF94C8FF)

@Composable
fun WeatherPage(state: WeatherState, settings: Settings) {
    if (state !is WeatherState.Ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                weatherMessage(state),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val w = state.weather
    val f = settings.fahrenheit
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Hero: icon, temperature, place and today's range.
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            WeatherIcon(w.code, w.isDay, 88.dp)
            Spacer(Modifier.width(20.dp))
            Text(
                formatTemp(w.tempC, f),
                fontSize = 96.sp,
                lineHeight = 96.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GoogleSans,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(24.dp))
            Column {
                Text(
                    w.place,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(weatherLabel(w.code, w.isDay), style = MaterialTheme.typography.titleLarge)
                Text(
                    "↑ ${formatTemp(w.highC, f)}   ↓ ${formatTemp(w.lowC, f)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Conditions as icon + value; captions only where the icon alone could be misread.
        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(R.drawable.wx_thermometer, formatTemp(w.feelsC, f), "feels like")
                Metric(R.drawable.wx_droplets, "${w.humidity}%", "humidity", RainBlue)
                WindMetric(w, f)
                Metric(R.drawable.wx_umbrella, "${w.rainChance}%", "rain today", RainBlue)
                Metric(R.drawable.wx_sun_medium, "UV ${w.uvMax.roundToInt()}", uvWord(w.uvMax), Color(0xFFFFD27A))
                w.sunrise?.let { Metric(R.drawable.wx_sunrise, it, "sunrise", Color(0xFFFFD27A)) }
                w.sunset?.let { Metric(R.drawable.wx_sunset, it, "sunset", Color(0xFFFFB27A)) }
            }
        }

        // Next six hours. The rain row only takes space when some hour has rain worth showing.
        val hours = w.hours.take(6)
        val anyRain = hours.any { it.rainChance >= 10 }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            hours.forEach { h ->
                Panel(Modifier.weight(1f), padding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            hourLabel(h.hour, settings.use24h),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        WeatherIcon(h.code, h.isDay, 28.dp, Modifier.padding(vertical = 4.dp))
                        Text(
                            formatTemp(h.tempC, f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (anyRain) Row(
                            Modifier.height(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (h.rainChance >= 10) {
                                Icon(
                                    painterResource(R.drawable.wx_droplets),
                                    contentDescription = "Chance of rain",
                                    tint = RainBlue,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text("${h.rainChance}%", style = MaterialTheme.typography.labelMedium, color = RainBlue)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(
    @DrawableRes icon: Int,
    value: String,
    caption: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconModifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = caption, tint = tint, modifier = iconModifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Wind speed with an arrow pointing the way the wind is blowing. */
@Composable
private fun WindMetric(w: Weather, imperial: Boolean) {
    val speed = if (imperial) "${(w.windKmh / 1.609).roundToInt()} mph" else "${w.windKmh.roundToInt()} km/h"
    Metric(
        R.drawable.wx_navigation_2,
        speed,
        "wind ${compass(w.windFromDeg)}",
        iconModifier = Modifier.rotate((w.windFromDeg + 180f) % 360f),
    )
}

private fun compass(deg: Int): String =
    listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((deg % 360) + 360) % 360 + 22) / 45 % 8]

private fun uvWord(uv: Double): String = when {
    uv < 3 -> "low"
    uv < 6 -> "moderate"
    uv < 8 -> "high"
    uv < 11 -> "very high"
    else -> "extreme"
}

private fun hourLabel(hour: Int, use24h: Boolean): String = when {
    use24h -> String.format(Locale.US, "%02d:00", hour)
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}
