package ai.zenkai.zenkai.config.login

import ai.zenkai.zenkai.model.TokenType
import com.julienvey.trello.Trello
import com.julienvey.trello.impl.TrelloImpl
import org.springframework.social.oauth1.AbstractOAuth1ServiceProvider
import org.springframework.social.oauth1.OAuth1Version

class TrelloServiceProvider(consumerKey: String, consumerSecret: String)
    : AbstractOAuth1ServiceProvider<Trello>(consumerKey, consumerSecret, TrelloOAuth1Template(consumerKey, consumerSecret,
        "https://trello.com/1/OAuthGetRequestToken",
        "https://trello.com/1/OAuthAuthorizeToken",
        "https://trello.com/1/OAuthGetAccessToken",
        TokenType.TRELLO.authParams,
        OAuth1Version.CORE_10_REVISION_A)) {

    override fun getApi(accessToken: String, secret: String): Trello {
        return TrelloImpl(consumerKey, accessToken)
    }
}