package ai.zenkai.zenkai.config.login

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.social.connect.Connection
import org.springframework.social.connect.web.SignInAdapter
import org.springframework.stereotype.Component
import org.springframework.web.context.request.NativeWebRequest

@Component
class MySignInAdapter : SignInAdapter {

    override fun signIn(userId: String, connection: Connection<*>, request: NativeWebRequest): String? {
        val authentication = PreAuthenticatedAuthenticationToken(userId, connection, setOf(SimpleGrantedAuthority("ROLE_USER")))

        SecurityContextHolder.getContext().authentication = authentication

        return null
    }

}