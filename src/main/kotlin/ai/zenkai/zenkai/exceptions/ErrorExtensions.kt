package ai.zenkai.zenkai.exceptions

import ai.zenkai.zenkai.model.Token
import ai.zenkai.zenkai.model.TokenType
import com.google.gson.Gson
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import javax.servlet.http.HttpServletResponse
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

fun <T> ok(body: T) = ResponseEntity(body, HttpStatus.OK)

fun badRequest(e: Exception, gson: Gson, res: HttpServletResponse) {
    jsonError(BotError(e.message, HttpStatus.BAD_REQUEST), gson, res)
}

fun jsonError(error: BotError, gson: Gson, res: HttpServletResponse) {
    res.setHeader("Content-Type", "application/json;charset=UTF-8")
    res.setStatus(error.status)
    res.writer.print(gson.toJson(error))
}

fun <R> Throwable.multicatch(vararg classes: KClass<*>, block: () -> R): R {
     if (classes.any { this::class.isSubclassOf(it) }) {
         return block()
     } else throw this
}

open class BotError(val message: String?, @Transient private val httpStatus: HttpStatus) {

    constructor(e: Exception, httpStatus: HttpStatus) : this(e.message, httpStatus)

    val status = httpStatus.value()
    val error = httpStatus.reasonPhrase

    fun getResponseEntity() = ResponseEntity(this, httpStatus)
}

class LoginError(val login: Token): BotError("Login required", HttpStatus.UNAUTHORIZED) {
    constructor(type: TokenType) : this(Token(type, null))
}

class BadRequestError(message: String?): BotError(message, HttpStatus.BAD_REQUEST) {
    constructor(e: Exception): this(e.message)
}