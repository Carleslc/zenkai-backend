package ai.zenkai.zenkai.services

import com.fasterxml.jackson.annotation.JsonIgnore

abstract class ServiceEntity<S: Service> {

    @JsonIgnore
    protected lateinit var service: S

    open fun attachService(service: S) {
        this.service = service
    }

}