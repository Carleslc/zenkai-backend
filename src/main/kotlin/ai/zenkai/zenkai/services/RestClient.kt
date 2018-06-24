package ai.zenkai.zenkai.services

import kotlin.reflect.KClass

interface RestClient {

    fun <T: Any> get(url: String, klass: KClass<T>, params: Parameters): T

    fun <T: Any> getList(url: String, klass: KClass<T>, params: Parameters): List<T>

    fun <T: Any> post(url: String, klass: KClass<T>, params: Parameters): T

    fun post(url: String, params: Parameters): HttpResponse

    fun put(url: String, params: Parameters)

    fun delete(url: String, params: Parameters)

}