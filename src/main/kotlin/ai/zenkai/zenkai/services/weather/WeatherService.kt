package ai.zenkai.zenkai.services.weather

import ai.zenkai.zenkai.model.Weather
import ai.zenkai.zenkai.services.Service

interface WeatherService : Service {

    val url: String

    fun getWeather(location: String, language: String): Weather?

}