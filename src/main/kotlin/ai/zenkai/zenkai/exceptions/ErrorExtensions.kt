package ai.zenkai.zenkai.exceptions

import com.google.gson.Gson
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import javax.servlet.http.HttpServletResponse
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

fun <T> ok(body: T) = ResponseEntity(body, HttpStatus.OK)

fun badRequest(message: String?) = ErrorJson(message, HttpStatus.BAD_REQUEST).getResponseEntity()

fun badRequest(e: Exception) = badRequest(e.message)

fun badRequest(e: Exception, gson: Gson, res: HttpServletResponse) {
    jsonError(ErrorJson(e.message, HttpStatus.BAD_REQUEST), gson, res)
}

fun jsonError(error: ErrorJson, gson: Gson, res: HttpServletResponse) {
    res.setHeader("Content-Type", "application/json;charset=UTF-8")
    res.setStatus(error.status)
    res.writer.print(gson.toJson(error))
}

fun <R> Throwable.multicatch(vararg classes: KClass<*>, block: () -> R): R {
     if (classes.any { this::class.isSubclassOf(it) }) {
         return block()
     } else throw this
}

data class ErrorJson(val message: String?, @Transient private val httpStatus: HttpStatus) {
    val status = httpStatus.value()
    val error = httpStatus.reasonPhrase

    fun getResponseEntity() = ResponseEntity(this, httpStatus)
}