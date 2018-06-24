package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.fixInt
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.model.Bot
import ai.zenkai.zenkai.model.Handler
import ai.zenkai.zenkai.replace
import org.springframework.stereotype.Controller

@Controller
class CalculatorController : BaseController {

    override val actionMap: Map<String, Handler> = mapOf(
            "calculator.sum" to { b -> b.sum() },
            "calculator.subtraction" to { b -> b.subtract() },
            "calculator.multiplication" to { b -> b.multiply() },
            "calculator.division" to { b -> b.divide() }
    )

    fun Bot.sum() {
        val a = getDouble("number1")
        val b = getDouble("number2")
        val result = a + b

        addMessage(get(S.SUM).replace("\$1" to fixInt(a), "\$2" to fixInt(b), "\$3" to fixInt(result)))
    }

    fun Bot.subtract() {
        val a = getDouble("number1")
        val b = getDouble("number2")
        val result = a - b
        addMessage(get(S.SUBSTRACT).replace("\$1" to fixInt(a), "\$2" to fixInt(b), "\$3" to fixInt(result)))
    }

    fun Bot.multiply() {
        val a = getDouble("number1")
        val b = getDouble("number2")
        val result = a * b
        addMessage(get(S.MULTIPLY).replace("\$1" to fixInt(a), "\$2" to fixInt(b), "\$3" to fixInt(result)))
    }

    fun Bot.divide() {
        val a = getDouble("number1")
        val b = getDouble("number2")
        if (b == 0.toDouble()) {
            addMessage(S.DIVIDE_ZERO)
        } else {
            val result = a / b
            addMessage(get(S.DIVIDE).replace("\$1" to fixInt(a), "\$2" to fixInt(b), "\$3" to fixInt(result)))
        }
    }

}