package ai.zenkai.zenkai.config

import me.carleslc.kotlin.extensions.standard.isNull
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.RememberMeAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import java.io.IOException
import javax.servlet.FilterChain
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class JwtAuthorizationFilter(authManager: AuthenticationManager) : BasicAuthenticationFilter(authManager) {

    @Throws(IOException::class, ServletException::class)
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val header = request.getHeader(AUTH_HEADER)

        if (header.isNull() || !header.startsWith(AUTH_PREFIX)) {
            chain.doFilter(request, response)
            return
        }

        SecurityContextHolder.getContext().authentication = getAuthentication(request)
        chain.doFilter(request, response)
    }

    private fun getAuthentication(request: HttpServletRequest): Authentication? {
        if (request.getHeader(AUTH_HEADER).removePrefix(AUTH_PREFIX) == SECRET) {
            return RememberMeAuthenticationToken("Authorization", SECRET, emptyList<GrantedAuthority>())
        }
        return null
    }

}