package ai.zenkai.zenkai.services

data class HttpResponse(val body: Any?, val status: Int) {

    fun isOk() = status == 200

}