package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.model.Bot
import ai.zenkai.zenkai.model.Handler
import ai.zenkai.zenkai.services.weather.WeatherService
import org.springframework.stereotype.Controller

@Controller
class WeatherController(private val weatherService: WeatherService) : BaseController {

    override val actionMap: Map<String, Handler> = mapOf(
            "weather" to { b -> b.weather() }
    )

    fun Bot.weather() {
        val location = getString("city")
        fill(weatherService.getWeather(location!!, language), get(S.CITY_NOT_FOUND)) {
            get(ai.zenkai.zenkai.i18n.S.WEATHER)
                    .replace("\$city", city)
                    .replace("\$temperature", temperature.toString())
                    .replace("\$description", description)
        }
    }

}