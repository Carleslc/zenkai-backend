package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.mergeMaps
import ai.zenkai.zenkai.model.Handler

interface BaseController {

    val actionMap: Map<String, Handler>

}

fun actionMapper(vararg controllers: BaseController): Map<String, Handler> = mergeMaps(controllers.map { it.actionMap })