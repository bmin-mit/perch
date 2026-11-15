package dev.perch.standby.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class Series(val values: List<Float>, val color: Color, val filled: Boolean = true)

/**
 * A line chart of the last [windowSec] one-second samples, newest on the right.
 * The y axis runs from 0 to [yMax] with a gridline and label at each of [yTicks];
 * the x axis is time, labelled in seconds ago.
 */
@Composable
fun TimeChart(
    series: List<Series>,
    yMax: Float,
    yTicks: List<Float>,
    yLabel: (Float) -> String,
    modifier: Modifier = Modifier,
    windowSec: Int = 60,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Canvas(modifier) {
        val yTexts = yTicks.map { measurer.measure(yLabel(it), labelStyle) }
        val xMarks = listOf(windowSec to "${windowSec}s", windowSec / 2 to "${windowSec / 2}s", 0 to "now")
        val xTexts = xMarks.map { measurer.measure(it.second, labelStyle) }

        val gutter = (yTexts.maxOfOrNull { it.size.width } ?: 0) + 8.dp.toPx()
        val bottom = (xTexts.maxOfOrNull { it.size.height } ?: 0) + 4.dp.toPx()
        val top = yTexts.firstOrNull()?.size?.height?.div(2f) ?: 0f
        val left = gutter
        val right = size.width - 4.dp.toPx()
        val plotBottom = size.height - bottom
        val plotH = plotBottom - top
        fun y(v: Float) = plotBottom - (v / yMax).coerceIn(0f, 1f) * plotH
        fun x(secondsAgo: Float) = right - secondsAgo / windowSec * (right - left)

        // Value axis: dashed gridlines with right-aligned labels.
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        yTicks.forEachIndexed { i, tick ->
            val ty = y(tick)
            drawLine(grid, Offset(left, ty), Offset(right, ty), 1.dp.toPx(), pathEffect = if (tick == 0f) null else dash)
            val t = yTexts[i]
            drawText(t, topLeft = Offset(gutter - 8.dp.toPx() - t.size.width, ty - t.size.height / 2f))
        }
        // Time axis labels under the plot.
        xMarks.forEachIndexed { i, (ago, _) ->
            val t = xTexts[i]
            val cx = x(ago.toFloat())
            val tx = (cx - t.size.width / 2f).coerceIn(left, right - t.size.width)
            drawText(t, topLeft = Offset(tx, plotBottom + 4.dp.toPx()))
        }

        series.forEach { s ->
            val values = s.values.takeLast(windowSec)
            if (values.size < 2) return@forEach
            val line = Path()
            values.forEachIndexed { i, v ->
                val px = x((values.size - 1 - i).toFloat())
                if (i == 0) line.moveTo(px, y(v)) else line.lineTo(px, y(v))
            }
            if (s.filled) {
                val fill = Path().apply {
                    addPath(line)
                    lineTo(right, plotBottom)
                    lineTo(x((values.size - 1).toFloat()), plotBottom)
                    close()
                }
                drawPath(
                    fill,
                    Brush.verticalGradient(listOf(s.color.copy(alpha = 0.3f), Color.Transparent), top, plotBottom),
                )
            }
            drawPath(line, s.color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(s.color, 3.dp.toPx(), Offset(right, y(values.last())))
        }
    }
}

/** Rounds up to 1, 2 or 5 × 10ⁿ so axis labels are tidy numbers. */
fun niceCeil(value: Float, floor: Float = 1f): Float {
    val v = value.coerceAtLeast(floor)
    val magnitude = 10.0.pow(floor(log10(v.toDouble()))).toFloat()
    val step = listOf(1f, 2f, 5f, 10f).first { it * magnitude >= v }
    return step * magnitude
}
