package ai.zenkai.zenkai.exceptions

class MissingRequiredArgumentException(argument: String) : Exception("Missing required argument: $argument")