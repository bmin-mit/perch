package dev.perch.standby

import android.app.Application
import android.content.Context
import dev.perch.standby.data.MacRepository
import dev.perch.standby.data.Prefs
import dev.perch.standby.data.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PerchApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/** Process-wide singletons shared by the activity and the screensaver. */
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs = Prefs(context)
    val mac = MacRepository(context, scope, prefs)
    val weather = WeatherRepository(context, scope, prefs)
}

val Context.graph: AppGraph get() = (applicationContext as PerchApp).graph
