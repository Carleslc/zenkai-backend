package ai.zenkai.zenkai.model

data class Token(val type: String?, val token: String?, val regex: String?, val loginEvent: String?) {

    constructor(type: TokenType, value: String?) : this(type.lower, value, type.regex.pattern, type.event)

}