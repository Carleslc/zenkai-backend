package ai.zenkai.zenkai.services

import com.fasterxml.jackson.databind.type.TypeFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import java.lang.reflect.Type
import kotlin.reflect.KClass

private val REST by lazy { RestTemplate() }

open class RestTemplateHttpClient {

    protected fun <T: Any> get(url: String, klass: KClass<T>, params: Parameters): T {
        return REST.getForObject(url, klass.java, params)!!
    }

    protected fun <T: Any> getList(url: String, klass: KClass<T>, params: Parameters): List<T> {
        return REST.exchange(url, HttpMethod.GET, null, getArrayType(klass), params).body?.toList() ?: listOf()
    }

    protected fun <T: Any> post(url: String, klass: KClass<T>, params: Parameters): T {
        return REST.postForObject(url, null, klass.java, params)!!
    }

    protected fun post(url: String, params: Parameters): HttpResponse {
        val response = REST.postForEntity(url, null, Any::class.java, params)
        return HttpResponse(response.body, response.statusCodeValue)
    }

    private fun <T: Any> getArrayType(klass: KClass<T>) = object : ParameterizedTypeReference<Array<T>>(){
        override fun getType(): Type {
            return TypeFactory.defaultInstance().constructArrayType(klass.java)
        }
    }

}