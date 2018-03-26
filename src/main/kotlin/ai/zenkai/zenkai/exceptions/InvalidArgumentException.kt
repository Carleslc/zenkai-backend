package ai.zenkai.zenkai.exceptions

class InvalidArgumentException(argument: String) : IllegalArgumentException("Invalid required argument: $argument")