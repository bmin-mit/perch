package dev.perch.standby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.perch.standby.AppGraph

private const val PAGES = 4

/**
 * The whole StandBy experience: Clock, Weather, Mac and Claude Code pages, swiped horizontally.
 * Hosted by both the activity and the screensaver.
 *
 * @param onRequestPermissions non-null only where a permission dialog can be shown (the activity);
 *   it also enables the settings button.
 */
@Composable
fun StandbyScreen(graph: AppGraph, onRequestPermissions: (() -> Unit)? = null) {
    val settings by graph.prefs.state.collectAsStateWithLifecycle()
    val mac by graph.mac.state.collectAsStateWithLifecycle()
    val weather by graph.weather.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // Only hold the Bluetooth link and poll weather while something is on screen.
    LifecycleStartEffect(graph) {
        graph.mac.acquire()
        graph.weather.acquire()
        onStopOrDispose {
            graph.mac.release()
            graph.weather.release()
        }
    }

    PerchTheme {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .windowInsetsPadding(WindowInsets.displayCutout)
            ) {
                // Nudge everything a few pixels each minute so static content can't burn in.
                val dx = (now.minute % 5 - 2) * 2
                val dy = (now.minute / 5 % 5 - 2) * 2
                val pager = rememberPagerState { PAGES }
                HorizontalPager(
                    state = pager,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(dx.dp.roundToPx(), dy.dp.roundToPx()) },
                    beyondViewportPageCount = 1,
                ) { page ->
                    Box(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 30.dp)) {
                        when (page) {
                            0 -> ClockPage(now, settings, weather, mac) { face ->
                                graph.prefs.update { it.copy(clockFace = face) }
                            }
                            1 -> WeatherPage(weather, settings)
                            2 -> MacPage(mac)
                            else -> ClaudePage(
                                mac,
                                now.atZone(java.time.ZoneId.systemDefault()).toEpochSecond(),
                                settings.use24h,
                            )
                        }
                    }
                }

                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(PAGES) { i ->
                        Box(
                            Modifier.size(6.dp).background(
                                if (i == pager.currentPage) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            )
                        )
                    }
                }

                if (onRequestPermissions != null) {
                    TextButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        Text("⚙", fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    if (showSettings) {
                        SettingsOverlay(
                            settings = settings,
                            onChange = graph.prefs::update,
                            onRequestPermissions = onRequestPermissions,
                            onClose = { showSettings = false },
                        )
                    }
                }
            }
        }
    }
}
