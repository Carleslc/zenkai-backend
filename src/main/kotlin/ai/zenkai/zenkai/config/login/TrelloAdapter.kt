package ai.zenkai.zenkai.config.login

import com.julienvey.trello.Trello
import org.springframework.social.connect.ApiAdapter
import org.springframework.social.connect.ConnectionValues
import org.springframework.social.connect.UserProfile
import org.springframework.social.connect.UserProfileBuilder
import org.springframework.web.client.HttpClientErrorException

class TrelloAdapter : ApiAdapter<Trello> {

    override fun test(trello: Trello): Boolean {
        return try {
            trello.getMemberInformation("me")
            true
        } catch (e: HttpClientErrorException) {
            false
        }
    }

    override fun setConnectionValues(trello: Trello, values: ConnectionValues) {
        val profile = trello.getMemberInformation("me")
        values.setProviderUserId(profile.id)
        values.setDisplayName(profile.email)
        values.setProfileUrl(profile.url)
    }

    override fun fetchUserProfile(trello: Trello): UserProfile {
        val profile = trello.getMemberInformation("me")
        return UserProfileBuilder()
                .setId(profile.id)
                .setName(profile.fullName)
                .setUsername(profile.username)
                .setEmail(profile.email)
                .build()
    }

    override fun updateStatus(trello: Trello, s: String) {
        // Not Supported
    }

}