package ai.zenkai.zenkai.config.login

import org.springframework.social.connect.Connection
import org.springframework.social.connect.ConnectionRepository
import org.springframework.social.connect.ConnectionSignUp
import org.springframework.social.connect.UsersConnectionRepository

class UserConnectionRepository : UsersConnectionRepository {

    override fun findUserIdsConnectedTo(p0: String?, p1: MutableSet<String>?): MutableSet<String> {
        TODO("not implemented")
    }

    override fun findUserIdsWithConnection(p0: Connection<*>?): MutableList<String> {
        TODO("not implemented")
    }

    override fun createConnectionRepository(p0: String?): ConnectionRepository {
        TODO("not implemented")
    }

    override fun setConnectionSignUp(p0: ConnectionSignUp?) {
        TODO("not implemented")
    }

}