package ai.zenkai.zenkai.config.login

import org.springframework.social.oauth1.OAuth1Parameters
import org.springframework.social.oauth1.OAuth1Template
import org.springframework.social.oauth1.OAuth1Version

class TrelloOAuth1Template(consumerKey: String, consumerSecret: String,
                           requestTokenUrl: String, authorizeUrl: String, accessTokenUrl: String,
                           var authParams: String?, version: OAuth1Version)
                           : OAuth1Template(consumerKey, consumerSecret, requestTokenUrl, authorizeUrl, accessTokenUrl, version) {

    override fun buildAuthorizeUrl(requestToken: String, parameters: OAuth1Parameters): String {
        return "${super.buildAuthorizeUrl(requestToken, parameters)}&$authParams"
    }

}