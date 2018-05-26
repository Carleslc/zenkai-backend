package ai.zenkai.zenkai.controllers.auth

import arrow.data.Try
import com.google.gson.Gson
import me.carleslc.kotlin.extensions.standard.getOrNull
import java.util.*

data class UserConfiguration(val userId: String, val language: String, val timezone: String) {

    fun encode(gson: Gson) = Base64.getEncoder().encodeToString(gson.toJson(this).toByteArray())

    companion object Parser {

        fun decode(userConfigurationEncoded: String, gson: Gson): UserConfiguration? {
            return Try {
                val userConfigurationJson = String(Base64.getDecoder().decode(userConfigurationEncoded))
                gson.fromJson(userConfigurationJson, UserConfiguration::class.java)
            }.getOrNull()
        }

    }

}