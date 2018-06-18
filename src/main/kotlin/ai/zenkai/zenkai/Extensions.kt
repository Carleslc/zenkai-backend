package ai.zenkai.zenkai

import me.carleslc.kotlin.extensions.number.round
import me.carleslc.kotlin.extensions.strings.isNotNullOrBlank
import java.text.BreakIterator
import java.text.Normalizer
import java.util.*
import kotlin.coroutines.experimental.buildSequence
import kotlin.math.roundToLong

fun fixInt(n: Double, decimals: Int = 5): String = if (n.toInt().toDouble() == n) n.toInt().toString() else n.round(decimals)

fun Double.roundToTenth(): Long = (this/10).roundToLong() * 10

fun String?.nullIfBlank() = if (isNotNullOrBlank()) this else null

fun String.remove(regex: Regex) = replace(regex, "")

fun String.clean(keepDigits: Boolean = true) = trim().remove(
        if (keepDigits) NON_WORD_CHARACTERS_EXCEPT_SPACES else NON_LETTER_CHARACTERS_EXCEPT_SPACES)

fun String.cleanFormat(locale: Locale) = clean(keepDigits = false).toLowerCase(locale).removeAccents()

fun String.words(locale: Locale): Sequence<String> {
    val text = this
    return buildSequence {
        val iterator = BreakIterator.getWordInstance(locale)
        iterator.setText(text)
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val word = text.substring(start, end)
            if (word.isNotBlank()) {
                yield(word)
            }
            start = end
            end = iterator.next()
        }
    }
}

fun String.titleize() = toLowerCase()
        .remove("[-_]".toRegex())
        .split(" ").joinToString(" ", transform = String::capitalize)

fun String.replace(args: Collection<Pair<String, String>>): String {
    var result = this
    args.forEach {
        result = result.replace(it.first, it.second)
    }
    return result
}

fun String.replace(vararg args: Pair<String, String>): String = replace(args.toList())

fun String.removeAccents() = Normalizer.normalize(this, Normalizer.Form.NFD).remove(DIACRITICAL_MARKS)

fun <K, V> Map<K, V>?.asMutableMap() = this as? MutableMap<K, V> ?: this?.toMutableMap() ?: mutableMapOf()

fun <K, V> Map<K, V>.add(entries: Map<K, V>) = asMutableMap().apply { putAll(entries) }

fun <K, V> Map<K, V>.add(vararg entries: Pair<K, V>) = asMutableMap().apply { putAll(entries) }

fun <T> Array<out T>.add(vararg elements: T) = toMutableList().apply { addAll(elements) }