package ai.zenkai.zenkai.controllers.auth

import ai.zenkai.zenkai.config.SERVER
import me.carleslc.kotlin.extensions.html.h
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
class GoogleApiAuthorizationController {

    companion object {
       const val URL = "/login/google"
       private const val SUCCESS = "<h2>Access to your Calendar has been authorized, return to Zenkai app.</h2>"
       private const val ERROR = "<h2>Access to your Calendar has NOT been authorized. Without it Zenkai cannot manage your calendar. If you change your opinion login again <a href=\"\$authUrl\">here</a>.</h2>"
    }

    @GetMapping(URL, params = ["userId"])
    fun authorize(req: HttpServletRequest, res: HttpServletResponse, @RequestParam(value = "userId") userIdEncoded: String) {
        val userId = String(Base64.getDecoder().decode(userIdEncoded))
        val auth = GoogleApiAuthorization(userId)
        if (auth.hasValidCredentials()) {
            renderHtml(res, SUCCESS)
        } else {
            res.sendRedirect(auth.getAuthorizationUrl())
        }
    }

    @GetMapping(URL, params = ["code", "state"])
    fun success(req: HttpServletRequest, res: HttpServletResponse,
                 @RequestParam(value = "code") code: String,
                 @RequestParam(value = "state") state: String) {
        val userId = String(Base64.getDecoder().decode(state))
        GoogleApiAuthorization(userId).setCredentials(code)
        renderHtml(res, SUCCESS)
    }

    @GetMapping(URL, params = ["error", "state"])
    fun error(req: HttpServletRequest, res: HttpServletResponse,
                  @RequestParam(value = "error") error: String,
                  @RequestParam(value = "state") state: String) {
        val userId = String(Base64.getDecoder().decode(state))
        renderHtml(res, ERROR.replace("\$authUrl",
                GoogleApiAuthorization(userId).getAuthorizationUrl()), HttpStatus.UNAUTHORIZED)
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