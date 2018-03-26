package ai.zenkai.zenkai.services.weather

import com.google.gson.JsonParser
import org.springframework.stereotype.Service
import java.io.FileNotFoundException
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

@Service
class OpenWeatherMapService : WeatherService {

    override val url = "https://api.openweathermap.org/data/2.5/weather"

    private val API_KEY = "0d9a3ca1aad7b7082a14b2e70d65121d"

    override fun getWeather(location: String, language: String): Weather? {
        val cityUrl = URLEncoder.encode(location, "UTF-8")
        return try {
            val response = URL("$url?q=$cityUrl&appid=$API_KEY&lang=$language").readText()
            val json = JsonParser().parse(response).asJsonObject
            val city = json["name"].asString
            val temperature = (json["main"].asJsonObject["temp"].asDouble - 273.15).roundToInt()
            val description = json.getAsJsonArray("weather")[0].asJsonObject["description"].asString
            Weather(city, description, temperature)
        } catch (e: FileNotFoundException) {
            null
        }
    }

}