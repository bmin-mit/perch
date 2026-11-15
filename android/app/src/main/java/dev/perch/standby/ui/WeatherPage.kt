package dev.perch.standby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.perch.standby.data.Settings
import dev.perch.standby.data.WeatherState
import java.util.Locale

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
    val (glyph, label) = weatherGlyph(w.code, w.isDay)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(glyph, fontSize = 88.sp)
            Spacer(Modifier.width(24.dp))
            Text(
                formatTemp(w.tempC, f),
                fontSize = 120.sp,
                lineHeight = 120.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(28.dp))
            Column {
                Text(
                    w.place,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(label, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "High ${formatTemp(w.highC, f)} · Low ${formatTemp(w.lowC, f)} · Feels ${formatTemp(w.feelsC, f)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            w.hours.take(6).forEach { h ->
                Panel(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            hourLabel(h.hour, settings.use24h),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(weatherGlyph(h.code, h.isDay).first, fontSize = 26.sp)
                        Text(
                            formatTemp(h.tempC, f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private fun hourLabel(hour: Int, use24h: Boolean): String = when {
    use24h -> String.format(Locale.US, "%02d:00", hour)
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}
