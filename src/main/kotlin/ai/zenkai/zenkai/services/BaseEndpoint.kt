package ai.zenkai.zenkai.services

interface BaseEndpoint {

    val url: String

    fun withParameters(parameters: Collection<String>): String {
        val builder = StringBuilder(url)
        builder.append('?')

        fun StringBuilder.appendParameter(param: String) {
            append(param).append("={").append(param).append('}')
        }

        val paramIterator = parameters.iterator()
        if (paramIterator.hasNext()) {
            builder.appendParameter(paramIterator.next())
        }
        paramIterator.forEachRemaining {
            builder.append('&').appendParameter(it)
        }
        return builder.toString()
    }

}