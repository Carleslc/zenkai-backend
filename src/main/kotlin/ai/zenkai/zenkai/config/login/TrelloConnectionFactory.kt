package ai.zenkai.zenkai.config.login

import ai.zenkai.zenkai.model.TokenType
import org.springframework.social.connect.support.OAuth1ConnectionFactory

import com.julienvey.trello.Trello

class TrelloConnectionFactory(appKey: String, appSecret: String)
    : OAuth1ConnectionFactory<Trello>(TokenType.TRELLO.toString(), TrelloServiceProvider(appKey, appSecret), TrelloAdapter())