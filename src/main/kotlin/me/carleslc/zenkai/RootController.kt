package me.carleslc.zenkai

import com.google.gson.JsonParser
import com.tmsdurham.actions.DialogflowApp
import main.java.com.tmsdurham.dialogflow.sample.DialogflowAction
import me.carleslc.kotlin.extensions.number.round
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.FileNotFoundException
import java.net.URL
import java.net.URLEncoder
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.math.roundToInt

@RestController
class RootController {

    val actionMap = mapOf(
            "checkVote" to ::checkVote,
            "sum" to ::sum,
            "substract" to ::substract,
            "multiply" to ::multiply,
            "divide" to ::divide,
            "weather" to ::weather
    )

    @GetMapping("/")
    fun root(@RequestParam(value = "name", defaultValue = "World") name: String) = "Hello $name"

    @PostMapping("/")
    fun intentMapper(req: HttpServletRequest, res: HttpServletResponse) {
        DialogflowAction(req, res).handleRequest(actionMap)
    }

    fun checkVote(action: DialogflowApp) {
        val age = action.getParameter("age", "amount").toDouble().toInt()
        val answer = if (age >= 18) "Yes" else "No"
        action.tell(answer)
    }

    fun fixInt(n: Double): String = if (n.toInt().toDouble() == n) n.toInt().toString() else n.round(5)

    fun sum(action: DialogflowApp) {
        val a = action.getParameter("number1").toDouble()
        val b = action.getParameter("number2").toDouble()
        val result = a + b
        action.tell("The sum of ${fixInt(a)} and ${fixInt(b)} is ${fixInt(result)}")
    }

    fun substract(action: DialogflowApp) {
        val a = action.getParameter("number1").toDouble()
        val b = action.getParameter("number2").toDouble()
        val result = a - b
        action.tell("The difference between ${fixInt(a)} and ${fixInt(b)} is ${fixInt(result)}")
    }

    fun multiply(action: DialogflowApp) {
        val a = action.getParameter("number1").toDouble()
        val b = action.getParameter("number2").toDouble()
        val result = a * b
        action.tell("${fixInt(a)} times ${fixInt(b)} is ${fixInt(result)}")
    }

    fun divide(action: DialogflowApp) {
        val a = action.getParameter("number1").toDouble()
        val b = action.getParameter("number2").toDouble()
        if (b == 0.toDouble()) {
            action.tell("Cannot divide by 0!")
        } else {
            val result = a / b
            action.tell("${fixInt(a)} divided by ${fixInt(b)} is ${fixInt(result)}")
        }
    }

    fun weather(action: DialogflowApp) {
        val cityUrl = URLEncoder.encode(action.getParameter("city"), "UTF-8")
        try {
            val response = URL("https://api.openweathermap.org/data/2.5/weather?q=$cityUrl&appid=0d9a3ca1aad7b7082a14b2e70d65121d").readText()
            val json = JsonParser().parse(response).asJsonObject
            val city = json["name"].asString
            val temperature = (json["main"].asJsonObject["temp"].asDouble - 273.15).roundToInt()
            val description = json.getAsJsonArray("weather")[0].asJsonObject["description"].asString
            action.tell("The weather in $city is $description with a temperature of $temperature degrees.")
        } catch (e: FileNotFoundException) {
            action.tell("City not found.")
        }
    }

    fun DialogflowApp.getParameter(vararg keys: String): String {
        val param = getArgument(keys[0])
        return if (keys.size > 1) (param as Map<*,*>)[keys[1]].toString() else param.toString()
    }

}