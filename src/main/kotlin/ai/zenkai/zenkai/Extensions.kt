package ai.zenkai.zenkai

import me.carleslc.kotlin.extensions.number.round
import me.carleslc.kotlin.extensions.strings.isNotNullOrBlank

fun fixInt(n: Double, decimals: Int = 5): String = if (n.toInt().toDouble() == n) n.toInt().toString() else n.round(decimals)

fun String?.nullIfBlank() = if (isNotNullOrBlank()) this else null

fun String.titleize() = toLowerCase()
        .replace("[-_]".toRegex(), " ")
        .split(" ").joinToString(" ", transform = String::capitalize)