package ai.zenkai.zenkai.controllers.auth

import ai.zenkai.zenkai.exceptions.badRequest
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.services.clock.toZoneIdOrThrow
import ai.zenkai.zenkai.services.events.GoogleCalendarEventService
import com.google.gson.Gson
import me.carleslc.kotlin.extensions.standard.letOrElse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.task.TaskExecutor
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
class GoogleApiAuthorizationController {

    @Autowired
    private val taskExecutor: TaskExecutor? = null

    @Autowired
    lateinit var gson: Gson

    companion object {
       const val URL = "/login/google"
       private const val SUCCESS = "<h2>Access to your Calendar has been authorized, return to Zenkai app.</h2>"
       private const val ERROR = "<h2>Access to your Calendar has NOT been authorized. Without it Zenkai cannot manage your calendar. If you change your opinion login again <a href=\"\$authUrl\">here</a>.</h2>"
    }

    @GetMapping(URL, params = ["state"])
    fun authorize(req: HttpServletRequest, res: HttpServletResponse, @RequestParam(value = "state") state: String) {
        withUserConfiguration(state, res) {
            val auth = GoogleApiAuthorization(userId)
            if (auth.hasValidCredentials()) {
                renderHtml(res, SUCCESS)
            } else {
                res.sendRedirect(auth.getAuthorizationUrl(state))
            }
        }
    }

    @GetMapping(URL, params = ["code", "state"])
    fun success(req: HttpServletRequest, res: HttpServletResponse,
                 @RequestParam(value = "code") code: String,
                 @RequestParam(value = "state") state: String) {
        withUserConfiguration(state, res) {
            val auth = GoogleApiAuthorization(userId)
            auth.setCredentials(code)
            renderHtml(res, SUCCESS)
            taskExecutor?.execute({ // create a new default calendar if not exists
                auth.getCalendar()?.let {
                    GoogleCalendarEventService(it,
                            timezone.toZoneIdOrThrow(),
                            i18n.ensureLanguage(language)).configure()
                }
            })
        }
    }

    @GetMapping(URL, params = ["error", "state"])
    fun error(req: HttpServletRequest, res: HttpServletResponse,
                  @RequestParam(value = "error") error: String,
                  @RequestParam(value = "state") state: String) {
        withUserConfiguration(state, res) {
            renderHtml(res, ERROR.replace("\$authUrl",
                    GoogleApiAuthorization(userId).getAuthorizationUrl(state)), HttpStatus.UNAUTHORIZED)
        }
    }

    private fun withUserConfiguration(state: String, res: HttpServletResponse, block: UserConfiguration.() -> Unit) {
        UserConfiguration.decode(state, gson).letOrElse({badRequest("Illegal State", res)}, block)
    }

    private fun renderHtml(res: HttpServletResponse, body: String, status: HttpStatus = HttpStatus.OK) {
        res.setStatus(status.value())
        res.contentType = "text/html"
        res.characterEncoding = "UTF-8"
        val writer = res.writer
        writer.println("<!doctype html><html><head>")
        writer.println("<meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\"></head><body>")
        writer.println(body)
        writer.println("</body></html>")
    }

}