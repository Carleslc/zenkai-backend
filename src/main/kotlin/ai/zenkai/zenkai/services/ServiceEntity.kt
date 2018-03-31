package ai.zenkai.zenkai.services

import com.fasterxml.jackson.annotation.JsonIgnore

open class ServiceEntity<S: Service> {

    @JsonIgnore
    lateinit var service: S

}