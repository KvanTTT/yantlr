import parser.AntlrToken
import parser.AntlrTokenType
import parser.antlrCharSetLiteralToEscapeChars
import parser.antlrStringLiteralToEscapeChars

const val DEFAULT_MODE_NAME = "DEFAULT_MODE"

fun AntlrToken.getCharCode(stringLiteral: Boolean): Int {
    val literalToEscapeChars =
        if (stringLiteral) antlrStringLiteralToEscapeChars else antlrCharSetLiteralToEscapeChars
    val value = value!!
    val code = when (type) {
        AntlrTokenType.Char -> value[0].code
        AntlrTokenType.EscapedChar -> value[1].let { literalToEscapeChars[it] ?: it }.code
        AntlrTokenType.UnicodeEscapedChar -> value.substring(2).toInt(16)
        else -> error("Unexpected token type: ${type}") // TODO: handle error tokens?
    }
    return code
}

fun <T> sortedSetOf(vararg elements: T): SortedSet<T> = elements.toCollection(SortedSet())

fun <K : Comparable<K>, V> sortedMapOf(vararg pairs: Pair<K, V>): SortedMap<K, V> = SortedMap<K, V>().apply { putAll(pairs) }