package atn

import parser.stringEscapeToLiteralChars

data class Interval(val start: Int, val end: Int = start) {
    init {
        require(start <= end) { "Start must be less than or equal to end" }
    }

    override fun toString(): String {
        return "[${start.renderElement()}" + (if (start != end) "..${end.renderElement()}]" else "]")
    }

    private fun Int.renderElement(): String {
        return toChar().let { char -> stringEscapeToLiteralChars[char]?.let { "\\" + it } ?: char }.toString()
    }
}