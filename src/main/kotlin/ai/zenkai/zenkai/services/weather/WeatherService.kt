package ai.zenkai.zenkai.services.weather

interface WeatherService {

    val url: String

    fun getWeather(location: String, language: String): Weather?

}