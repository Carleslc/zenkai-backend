package ai.zenkai.zenkai.services.weather

import ai.zenkai.zenkai.config.OPEN_WEATHER_MAP_API_KEY
import ai.zenkai.zenkai.model.Weather
import com.google.gson.JsonParser
import org.springframework.stereotype.Service
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

@Service
class OpenWeatherMapService : WeatherService {

    override val url = "https://api.openweathermap.org/data/2.5/weather"

    override fun getWeather(location: String, language: String): Weather? {
        val cityUrl = URLEncoder.encode(location, "UTF-8")
        return try {
            val response = URL("$url?q=$cityUrl&appid=$OPEN_WEATHER_MAP_API_KEY&lang=$language").readText()
            val json = JsonParser().parse(response).asJsonObject
            val city = json["name"].asString
            val temperature = (json["main"].asJsonObject["temp"].asDouble - 273.15).roundToInt()
            val description = json.getAsJsonArray("weather")[0].asJsonObject["description"].asString
            Weather(city, description, temperature)
        } catch (e: Exception) {
            null
        }
    }

}