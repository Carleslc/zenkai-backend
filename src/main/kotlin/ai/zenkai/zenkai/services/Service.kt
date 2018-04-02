package ai.zenkai.zenkai.services

interface Service

typealias Parameter = Pair<String, String>
typealias Parameters = Map<String, String>

fun parameters(vararg params: Parameter): Parameters = mutableMapOf(*params)