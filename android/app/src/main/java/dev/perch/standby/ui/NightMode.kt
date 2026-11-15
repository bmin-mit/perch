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

// Luminance into the red channel only, at reduced strength: everything, emoji included,
// becomes a dim red that is easy on dark-adapted eyes.
private val nightEffect = RenderEffect.createColorFilterEffect(
    ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                0.30f, 0.59f, 0.11f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        ).apply { postConcat(ColorMatrix().apply { setScale(0.8f, 1f, 1f, 1f) }) }
    )
).asComposeRenderEffect()

fun Modifier.nightFilter(on: Boolean): Modifier =
    if (on) graphicsLayer { renderEffect = nightEffect } else this
