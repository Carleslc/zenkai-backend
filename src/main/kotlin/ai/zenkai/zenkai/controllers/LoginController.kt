package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.model.Handler
import org.springframework.stereotype.Controller

@Controller
class LoginController : BaseController {

    override val actionMap: Map<String, Handler> = mapOf(
            "greetings" to { b -> b.greetings() },
            "login" to { b -> b.login() },
            "logout" to { b -> b.logout() }
    )

}