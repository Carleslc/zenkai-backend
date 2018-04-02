package ai.zenkai.zenkai.services.tasks.trello

import ai.zenkai.zenkai.i18n.toLocale
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class Member(val id: String,
                  val avatarHash: String?,
                  val bio: String?,
                  val confirmed: Boolean,
                  val fullName: String,
                  val initials: String,
                  val memberType: String,
                  val status: String,
                  val url: String,
                  val username: String,
                  val avatarSource: String?,
                  val email: String?,
                  val gravatarHash: String?,
                  val idBoards: List<String>,
                  val idEnterprise: String?,
                  val idOrganizations: List<String>?,
                  val idEnterprisesAdmin: List<String>?,
                  val idPremOrgsAdmin: List<String>?,
                  val loginTypes: List<String>?,
                  val products: List<Int>?,
                  val prefs: Preferences,
                  val boards: List<Board>?) : TrelloEntity() {

    fun getLocale(): Locale = Locale(prefs.locale)

    override fun attachService(service: Trello) {
        super.attachService(service)
        boards?.forEach { it.attachService(service) }
    }
}