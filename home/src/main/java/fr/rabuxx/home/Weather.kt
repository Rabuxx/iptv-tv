package fr.rabuxx.home

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Weather(val temperature: Int, val code: Int)

/** Météo de l'accueil, fournie par Open-Meteo, sans clé d'API. */
object WeatherApi {
    const val CITY = "Bordeaux"
    private const val LATITUDE = 44.84
    private const val LONGITUDE = -0.58
    private const val URL_FORECAST = "https://api.open-meteo.com/v1/forecast" +
        "?latitude=$LATITUDE&longitude=$LONGITUDE&current=temperature_2m,weather_code&timezone=auto"

    /** Null en cas de souci réseau : le widget disparaît simplement. */
    fun load(): Weather? = runCatching {
        val connection = URL(URL_FORECAST).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            parseWeather(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

fun parseWeather(json: String): Weather {
    val current = JSONObject(json).getJSONObject("current")
    return Weather(
        temperature = Math.round(current.getDouble("temperature_2m")).toInt(),
        code = current.getInt("weather_code"),
    )
}

/** Symbole d'un code météo WMO. */
fun weatherIcon(code: Int): String = when (code) {
    0, 1 -> "☀"
    2 -> "⛅"
    3 -> "☁"
    45, 48 -> "🌫"
    in 51..67, in 80..82 -> "🌧"
    in 71..77, 85, 86 -> "❄"
    in 95..99 -> "⛈"
    else -> "☁"
}
