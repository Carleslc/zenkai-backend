package ai.zenkai.zenkai.services.weather

import ai.zenkai.zenkai.services.Service

interface WeatherService : Service {

    fun getWeather(location: String, language: String): Weather?

}