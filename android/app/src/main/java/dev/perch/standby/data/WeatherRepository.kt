package dev.perch.standby.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

data class HourCast(val hour: Int, val tempC: Double, val code: Int, val isDay: Boolean)

data class Weather(
    val place: String,
    val tempC: Double,
    val feelsC: Double,
    val code: Int,
    val isDay: Boolean,
    val highC: Double,
    val lowC: Double,
    val hours: List<HourCast>,
)

sealed interface WeatherState {
    data object Loading : WeatherState
    data object NeedsLocation : WeatherState
    data class Failed(val message: String) : WeatherState
    data class Ready(val weather: Weather) : WeatherState
}

private const val REFRESH_MS = 30 * 60 * 1000L
private const val RETRY_MS = 60 * 1000L

/** Weather from Open-Meteo (no API key), for a typed city or the device's coarse location. */
class WeatherRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val prefs: Prefs,
) {
    private val context = context.applicationContext
    private val _state = MutableStateFlow<WeatherState>(WeatherState.Loading)
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    private val trigger = MutableStateFlow(0)
    private var users = 0
    private var job: Job? = null

    fun acquire() {
        if (users++ > 0) return
        job = scope.launch {
            val city = prefs.state.map { it.city.trim() }.distinctUntilChanged()
            combine(city, trigger) { c, _ -> c }.collectLatest { c ->
                while (true) {
                    load(c)
                    delay(if (_state.value is WeatherState.Ready) REFRESH_MS else RETRY_MS)
                }
            }
        }
    }

    fun release() {
        if (--users > 0) return
        users = 0
        job?.cancel()
        job = null
    }

    /** Re-fetch now, e.g. after the location permission was granted. */
    fun refresh() {
        trigger.value++
    }

    private suspend fun load(city: String) {
        val previous = _state.value
        if (previous !is WeatherState.Ready) _state.value = WeatherState.Loading
        _state.value = try {
            withContext(Dispatchers.IO) { fetch(city) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep showing the last good reading through a transient network failure.
            if (previous is WeatherState.Ready && previous.weather.place.equals(city, true)) previous
            else WeatherState.Failed("Couldn't load weather")
        }
    }

    private suspend fun fetch(city: String): WeatherState {
        val lat: Double
        val lon: Double
        val place: String
        if (city.isNotEmpty()) {
            val q = URLEncoder.encode(city, "UTF-8")
            val results = JSONObject(
                httpGet("https://geocoding-api.open-meteo.com/v1/search?count=1&name=$q")
            ).optJSONArray("results")
            val hit = results?.optJSONObject(0)
                ?: return WeatherState.Failed("City \"$city\" not found")
            lat = hit.getDouble("latitude")
            lon = hit.getDouble("longitude")
            place = hit.optString("name", city)
        } else {
            val granted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return WeatherState.NeedsLocation
            val loc = deviceLocation()
                ?: return WeatherState.Failed("No location yet. Turn on location or set a city")
            lat = loc.latitude
            lon = loc.longitude
            place = placeName(lat, lon) ?: "Current location"
        }

        // Two decimals (~1 km) is plenty for a forecast and avoids sending a precise position.
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${"%.2f".format(Locale.US, lat)}&longitude=${"%.2f".format(Locale.US, lon)}" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day" +
            "&hourly=temperature_2m,weather_code,is_day&forecast_hours=7" +
            "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"
        val json = JSONObject(httpGet(url))
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val hourly = json.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val hours = (1 until times.length()).map { i ->
            HourCast(
                hour = times.getString(i).substring(11, 13).toInt(),
                tempC = hourly.getJSONArray("temperature_2m").getDouble(i),
                code = hourly.getJSONArray("weather_code").getInt(i),
                isDay = hourly.getJSONArray("is_day").getInt(i) == 1,
            )
        }
        return WeatherState.Ready(
            Weather(
                place = place,
                tempC = current.getDouble("temperature_2m"),
                feelsC = current.getDouble("apparent_temperature"),
                code = current.getInt("weather_code"),
                isDay = current.getInt("is_day") == 1,
                highC = daily.getJSONArray("temperature_2m_max").getDouble(0),
                lowC = daily.getJSONArray("temperature_2m_min").getDouble(0),
                hours = hours,
            )
        )
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun deviceLocation(): Location? {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        try {
            val providers = lm.getProviders(true)
            providers.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { return it }
            val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .firstOrNull { it in providers } ?: return null
            return withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { cont ->
                    lm.getCurrentLocation(provider, null, context.mainExecutor) { cont.resume(it) }
                }
            }
        } catch (e: SecurityException) {
            return null
        }
    }

    private suspend fun placeName(lat: Double, lon: Double): String? = withTimeoutOrNull(5_000) {
        suspendCancellableCoroutine { cont ->
            try {
                Geocoder(context).getFromLocation(lat, lon, 1) { list ->
                    val a = list.firstOrNull()
                    cont.resume(a?.locality ?: a?.subAdminArea ?: a?.adminArea)
                }
            } catch (e: Exception) {
                cont.resume(null)
            }
        }
    }
}
