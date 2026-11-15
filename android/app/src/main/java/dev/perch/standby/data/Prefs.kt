package dev.perch.standby.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val use24h: Boolean = true,
    val fahrenheit: Boolean = false,
    /** Show simulated Mac data instead of connecting over Bluetooth. */
    val demoMac: Boolean = false,
    /** Blank means "use the device location". */
    val city: String = "",
    val clockFace: Int = 0,
    /** Switch to a dim red, monochrome look when the room is dark. */
    val autoNight: Boolean = true,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("perch", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        sp.edit()
            .putBoolean("use24h", next.use24h)
            .putBoolean("fahrenheit", next.fahrenheit)
            .putBoolean("demoMac", next.demoMac)
            .putString("city", next.city)
            .putInt("clockFace", next.clockFace)
            .putBoolean("autoNight", next.autoNight)
            .apply()
    }

    private fun load() = Settings(
        use24h = sp.getBoolean("use24h", true),
        fahrenheit = sp.getBoolean("fahrenheit", false),
        // The emulator cannot reach a real Mac over Bluetooth, so default to demo data there.
        demoMac = sp.getBoolean("demoMac", isEmulator()),
        city = sp.getString("city", "") ?: "",
        clockFace = sp.getInt("clockFace", 0),
        autoNight = sp.getBoolean("autoNight", true),
    )

    private fun isEmulator() =
        Build.HARDWARE == "ranchu" || Build.PRODUCT.startsWith("sdk_gphone")
}
