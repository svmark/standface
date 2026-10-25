package com.marksnd.foldstandby.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.time.LocalDateTime
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** 지금 시각을 포함한 시간별 예보 개수. */
private const val HOURLY_COUNT = 7

data class HourlyWeather(val time: LocalDateTime, val temp: Int, val code: Int, val isDay: Boolean)

data class Weather(
    val place: String,
    val temp: Int,
    val code: Int,
    val isDay: Boolean,
    val high: Int,
    val low: Int,
    val feelsLike: Int,
    val humidity: Int,
    val windMs: Int,
    val hourly: List<HourlyWeather>,
    val fetchedAt: LocalDateTime,
)

sealed interface WeatherState {
    data object Loading : WeatherState
    data object Failed : WeatherState
    data class Ready(val weather: Weather) : WeatherState
}

enum class Sky { Clear, PartlyCloudy, Cloudy, Fog, Rain, Snow, Storm }

/** WMO 날씨 코드 → 하늘 상태. https://open-meteo.com/en/docs */
fun skyOf(code: Int): Sky = when (code) {
    0, 1 -> Sky.Clear
    2 -> Sky.PartlyCloudy
    3 -> Sky.Cloudy
    45, 48 -> Sky.Fog
    in 51..67, in 80..82 -> Sky.Rain
    in 71..77, 85, 86 -> Sky.Snow
    in 95..99 -> Sky.Storm
    else -> Sky.Cloudy
}

fun skyLabel(sky: Sky, korean: Boolean): String = when (sky) {
    Sky.Clear -> if (korean) "맑음" else "Clear"
    Sky.PartlyCloudy -> if (korean) "구름 조금" else "Partly cloudy"
    Sky.Cloudy -> if (korean) "흐림" else "Cloudy"
    Sky.Fog -> if (korean) "안개" else "Fog"
    Sky.Rain -> if (korean) "비" else "Rain"
    Sky.Snow -> if (korean) "눈" else "Snow"
    Sky.Storm -> if (korean) "뇌우" else "Thunderstorm"
}

private const val SEOUL_LAT = 37.5665
private const val SEOUL_LON = 126.9780

private fun hasLocationPermission(context: Context) =
    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** 위치 찾기 결과. 서울로 대신할 때도 왜 그런지 구분해 장소 이름에 표시한다. */
internal sealed interface LocationLookup {
    data class Found(val lat: Double, val lon: Double) : LocationLookup
    data object NoPermission : LocationLookup
    data object ServicesOff : LocationLookup
    data object NotFound : LocationLookup
}

/** 캐시된 위치가 이보다 오래됐으면 새로 받는다. */
internal const val FRESH_LOCATION_MS = 3 * 60 * 60_000L

/** 새 위치를 기다리는 최대 시간. 넘으면 오래된 캐시나 서울로 대신한다. */
private const val CURRENT_LOCATION_TIMEOUT_MS = 15_000L

/**
 * 캐시된 위치가 충분히 새것이면 그대로 쓰고, 없거나 오래됐으면 위치 서비스가 켜져 있을 때 새로 받는다.
 * 새로 받지 못하면 오래된 캐시라도 쓰고, 그것도 없으면 왜 없는지 돌려준다.
 */
internal suspend fun resolveLocation(
    hasPermission: Boolean,
    servicesOn: Boolean,
    cached: LocationLookup.Found?,
    cachedAgeMs: Long?,
    fetchCurrent: suspend () -> LocationLookup.Found?,
): LocationLookup {
    if (!hasPermission) return LocationLookup.NoPermission
    if (cached != null && cachedAgeMs != null && cachedAgeMs in 0..FRESH_LOCATION_MS) return cached
    if (!servicesOn) return cached ?: LocationLookup.ServicesOff
    return fetchCurrent() ?: cached ?: LocationLookup.NotFound
}

private fun Location.toFound() = LocationLookup.Found(latitude, longitude)

private fun lastKnown(lm: LocationManager): Location? =
    listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.elapsedRealtimeNanos }

/** 켜져 있는 제공자 중 하나로 지금 위치를 한 번 받는다. 시간 안에 못 받으면 null. */
private suspend fun currentLocation(lm: LocationManager): Location? {
    val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .firstOrNull { lm.hasProvider(it) && lm.isProviderEnabled(it) } ?: return null
    return withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            try {
                lm.getCurrentLocation(provider, signal, Runnable::run) { if (cont.isActive) cont.resume(it) }
            } catch (_: SecurityException) {
                cont.resume(null)
            } catch (_: IllegalArgumentException) {
                cont.resume(null)
            }
        }
    }
}

private suspend fun findLocation(context: Context): LocationLookup {
    val permitted = hasLocationPermission(context)
    val lm = context.getSystemService(LocationManager::class.java)
        ?: return if (permitted) LocationLookup.NotFound else LocationLookup.NoPermission
    return try {
        val cached = if (permitted) lastKnown(lm) else null
        resolveLocation(
            hasPermission = permitted,
            servicesOn = lm.isLocationEnabled,
            cached = cached?.toFound(),
            cachedAgeMs = cached?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 },
            fetchCurrent = { currentLocation(lm)?.toFound() },
        )
    } catch (_: SecurityException) {
        LocationLookup.NoPermission
    }
}

/** 서울로 대신할 때의 장소 이름. 권한은 있는데 위치를 못 받았으면 그 이유를 붙인다. */
internal fun fallbackPlace(lookup: LocationLookup, korean: Boolean): String {
    val seoul = if (korean) "서울" else "Seoul"
    return when (lookup) {
        LocationLookup.ServicesOff -> if (korean) "$seoul · 위치 꺼짐" else "$seoul · location off"
        LocationLookup.NotFound -> if (korean) "$seoul · 위치 못 찾음" else "$seoul · no location"
        else -> seoul
    }
}

@Suppress("DEPRECATION")
private fun placeName(context: Context, lat: Double, lon: Double, korean: Boolean): String {
    val locale = if (korean) Locale.KOREAN else Locale.ENGLISH
    val address = runCatching { Geocoder(context, locale).getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
    return address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: if (korean) "현재 위치" else "Here"
}

/** Open-Meteo는 무료이고 API 키가 필요 없다. 위치를 알 수 없으면(권한 없음·위치 꺼짐·못 찾음) 서울 날씨를 보여준다. */
suspend fun fetchWeather(context: Context, korean: Boolean): Weather = withContext(Dispatchers.IO) {
    val lookup = findLocation(context)
    val loc = lookup as? LocationLookup.Found
    val lat = loc?.lat ?: SEOUL_LAT
    val lon = loc?.lon ?: SEOUL_LON
    val place = if (loc == null) fallbackPlace(lookup, korean) else placeName(context, lat, lon, korean)
    val url = URL(
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day,apparent_temperature,relative_humidity_2m,wind_speed_10m" +
            "&hourly=temperature_2m,weather_code,is_day&forecast_hours=$HOURLY_COUNT" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&wind_speed_unit=ms&timezone=auto&forecast_days=2",
    )
    val conn = url.openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 10_000
    try {
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val hourly = json.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val days = hourly.getJSONArray("is_day")
        Weather(
            place = place,
            temp = current.getDouble("temperature_2m").roundToInt(),
            code = current.getInt("weather_code"),
            isDay = current.getInt("is_day") == 1,
            high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            feelsLike = current.getDouble("apparent_temperature").roundToInt(),
            humidity = current.getDouble("relative_humidity_2m").roundToInt(),
            windMs = current.getDouble("wind_speed_10m").roundToInt(),
            hourly = (0 until times.length()).map {
                HourlyWeather(LocalDateTime.parse(times.getString(it)), temps.getDouble(it).roundToInt(), codes.getInt(it), days.getInt(it) == 1)
            },
            fetchedAt = LocalDateTime.now(),
        )
    } finally {
        conn.disconnect()
    }
}

/** 30분마다 새로 받고, 실패하면 5분 뒤 다시 시도한다. */
@Composable
fun rememberWeather(korean: Boolean): State<WeatherState> {
    val context = LocalContext.current
    return produceState<WeatherState>(WeatherState.Loading, korean) {
        while (true) {
            val result = runCatching { fetchWeather(context, korean) }
            result.onSuccess { value = WeatherState.Ready(it) }
                .onFailure { if (value !is WeatherState.Ready) value = WeatherState.Failed }
            delay(if (result.isSuccess) 30 * 60_000L else 5 * 60_000L)
        }
    }
}
