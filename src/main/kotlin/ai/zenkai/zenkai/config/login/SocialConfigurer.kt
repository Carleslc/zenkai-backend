package ai.zenkai.zenkai.config.login

import ai.zenkai.zenkai.model.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.security.core.context.SecurityContext
import org.springframework.social.UserIdSource
import org.springframework.social.config.annotation.ConnectionFactoryConfigurer
import org.springframework.social.config.annotation.EnableSocial
import org.springframework.social.config.annotation.SocialConfigurerAdapter
import org.springframework.social.connect.ConnectionFactoryLocator
import org.springframework.social.connect.ConnectionRepository
import org.springframework.social.connect.UsersConnectionRepository
import org.springframework.social.connect.web.ConnectController
import org.springframework.social.connect.web.ProviderSignInUtils

@Configuration
@EnableSocial
class SocialConfig : SocialConfigurerAdapter() {

    @Autowired
    private lateinit var user: User

    override fun addConnectionFactories(connectionFactoryConfigurer: ConnectionFactoryConfigurer, environment: Environment) {
        connectionFactoryConfigurer.addConnectionFactory(TrelloConnectionFactory(
                environment.getProperty("spring.social.trello.app-id")!!,
                environment.getProperty("spring.social.trello.app-secret")!!))
    }

    override fun getUserIdSource(): UserIdSource {
        return user
    }

    override fun getUsersConnectionRepository(connectionFactoryLocator: ConnectionFactoryLocator): UsersConnectionRepository {
        return UserConnectionRepository()
    }

    @Bean
    fun connectController(connectionFactoryLocator: ConnectionFactoryLocator, connectionRepository: ConnectionRepository): ConnectController {
        return ConnectController(connectionFactoryLocator, connectionRepository)
    }

    @Bean
    fun providerSignInUtils(connectionFactoryLocator: ConnectionFactoryLocator, usersConnectionRepository: UsersConnectionRepository): ProviderSignInUtils {
        return ProviderSignInUtils(connectionFactoryLocator, usersConnectionRepository)
    }
}