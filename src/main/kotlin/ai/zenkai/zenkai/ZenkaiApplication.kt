package ai.zenkai.zenkai

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ZenkaiApplication

fun main(args: Array<String>) {
    runApplication<ZenkaiApplication>(*args)
}