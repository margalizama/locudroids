package com.locudroids.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

data class WeatherInfo(
    val city: String,
    val temperatureC: Int,
    val feelsLikeC: Int,
    val description: String,
    val windKmh: Int,
    val isRaining: Boolean,
)

/** Clima actual con Open-Meteo (gratis, sin clave). */
class WeatherClient {

    private data class Geo(val query: String, val name: String, val lat: Double, val lon: Double)

    private var geo: Geo? = null
    private var cached: Pair<Long, WeatherInfo>? = null
    private var cachedQuery: String = ""

    suspend fun current(city: String): WeatherInfo? {
        val query = city.trim()
        if (query.isEmpty()) return null
        val now = System.currentTimeMillis()
        cached?.let { (time, info) ->
            if (query == cachedQuery && now - time < CACHE_MS) return info
        }
        return withContext(Dispatchers.IO) {
            try {
                val place = resolve(query)
                val url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${place.lat}&longitude=${place.lon}" +
                    "&current=temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m" +
                    "&timezone=auto"
                val cur = JSONObject(Http.get(url)).getJSONObject("current")
                val code = cur.getInt("weather_code")
                val info = WeatherInfo(
                    city = place.name,
                    temperatureC = cur.getDouble("temperature_2m").roundToInt(),
                    feelsLikeC = cur.getDouble("apparent_temperature").roundToInt(),
                    description = describe(code),
                    windKmh = cur.getDouble("wind_speed_10m").roundToInt(),
                    isRaining = cur.optDouble("precipitation", 0.0) > 0.0 || code in RAIN_CODES,
                )
                cached = System.currentTimeMillis() to info
                cachedQuery = query
                info
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo obtener el clima: ${e.message}")
                null
            }
        }
    }

    private fun resolve(query: String): Geo {
        geo?.let { if (it.query == query) return it }
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=${URLEncoder.encode(query, "UTF-8")}&count=1&language=es&format=json"
        val results = JSONObject(Http.get(url)).optJSONArray("results")
            ?: throw IllegalStateException("Ciudad no encontrada: $query")
        val first = results.getJSONObject(0)
        val resolved = Geo(
            query = query,
            name = first.optString("name", query),
            lat = first.getDouble("latitude"),
            lon = first.getDouble("longitude"),
        )
        geo = resolved
        return resolved
    }

    companion object {
        private const val TAG = "WeatherClient"
        private const val CACHE_MS = 15 * 60 * 1000L
        private val RAIN_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)

        /** Código WMO -> descripción corta en español. */
        fun describe(code: Int): String = when (code) {
            0 -> "cielo despejado"
            1 -> "mayormente despejado"
            2 -> "parcialmente nublado"
            3 -> "nublado"
            45, 48 -> "niebla"
            51, 53, 55 -> "llovizna"
            56, 57 -> "llovizna helada"
            61 -> "lluvia ligera"
            63 -> "lluvia moderada"
            65 -> "lluvia fuerte"
            66, 67 -> "lluvia helada"
            71, 73, 75 -> "nieve"
            77 -> "granizo fino"
            80, 81, 82 -> "chubascos"
            85, 86 -> "chubascos de nieve"
            95 -> "tormenta"
            96, 99 -> "tormenta con granizo"
            else -> "clima variable"
        }
    }
}
