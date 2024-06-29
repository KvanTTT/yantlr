package atn

import parser.stringEscapeToLiteralChars

data class Interval(val start: Int, val end: Int = start) {
    companion object {
        const val MIN = Int.MIN_VALUE
        const val MAX = Int.MAX_VALUE - 1
    }

    init {
        require(start <= end) { "Start must be less than or equal to end" }
    }

    override fun toString(): String {
        return "[${start.renderElement()}" + (if (start != end) "..${end.renderElement()}]" else "]")
    }

    private fun Int.renderElement(): String {
        return when (this) {
            MIN -> return "-∞"
            MAX -> return "+∞"
            else -> toChar().let { char -> stringEscapeToLiteralChars[char]?.let { "\\" + it } ?: char }.toString()
        }
    }
}