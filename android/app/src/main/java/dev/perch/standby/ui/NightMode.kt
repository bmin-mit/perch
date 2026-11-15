package dev.perch.standby.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

private const val DARK_LUX = 3f
private const val BRIGHT_LUX = 12f

/**
 * True while the room is dark, from the ambient light sensor. The two thresholds keep it
 * from flickering when the light hovers around one value.
 */
@Composable
fun rememberIsDarkRoom(enabled: Boolean): Boolean {
    val context = LocalContext.current
    var dark by remember { mutableStateOf(false) }
    DisposableEffect(enabled) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (!enabled || sm == null || sensor == null) {
            dark = false
            return@DisposableEffect onDispose {}
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val lux = event.values[0]
                if (lux < DARK_LUX) dark = true else if (lux > BRIGHT_LUX) dark = false
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(listener) }
    }
    return dark
}

/** Night mode colours. Red preserves dark adaptation best; the others are for comfort. */
enum class NightTint(val label: String, val color: Color) {
    Red("Red", Color(0xFFE5483D)),
    Amber("Amber", Color(0xFFE8A33A)),
    Green("Green", Color(0xFF5FBF6E)),
    White("Soft white", Color(0xFFB8B4AC)),
    Blue("Blue", Color(0xFF6E9BE8)),
    ;

    companion object {
        fun of(index: Int) = entries.getOrElse(index) { Red }
    }
}

/**
 * Maps each pixel's luminance onto a single tint, so the whole screen (icons and graphs
 * included) becomes one dim colour that is easy on dark-adapted eyes.
 */
private fun tintEffect(tint: Color) = RenderEffect.createColorFilterEffect(
    ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                0.30f * tint.red, 0.59f * tint.red, 0.11f * tint.red, 0f, 0f,
                0.30f * tint.green, 0.59f * tint.green, 0.11f * tint.green, 0f, 0f,
                0.30f * tint.blue, 0.59f * tint.blue, 0.11f * tint.blue, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
).asComposeRenderEffect()

private val effects = NightTint.entries.associateWith { tintEffect(it.color) }

fun Modifier.nightFilter(on: Boolean, tint: NightTint): Modifier =
    if (on) graphicsLayer { renderEffect = effects.getValue(tint) } else this
