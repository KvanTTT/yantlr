package atn

import parser.stringEscapeToLiteralChars

data class Interval(val start: Int, val end: Int = start) {
    companion object {
        const val MIN = Int.MIN_VALUE
        const val MAX = Int.MAX_VALUE - 1
        val Empty = Interval(0, -1)
        val Full = Interval(MIN, MAX)
    }

    val isEmpty: Boolean
        get() = this == Empty

    val isFull: Boolean
        get() = this == Full

    override fun toString(): String {
        if (isEmpty) return "∅"
        return "[${start.renderElement()}" + (if (start != end) "..${end.renderElement()}]" else "]")
    }

    private fun Int.renderElement(): String {
        return when (this) {
            MIN -> return "-∞"
            MAX -> return "+∞"
            else -> toChar().let { char -> stringEscapeToLiteralChars[char]?.let { "\\" + it } ?: char }.toString()
        }
    }

    fun negate(): List<Interval> {
        if (isEmpty) return listOf(Full)

        return buildList {
            if (start > MIN) add(Interval(MIN, start - 1))
            if (end < MAX) add(Interval(end + 1, MAX))
        }
    }
}