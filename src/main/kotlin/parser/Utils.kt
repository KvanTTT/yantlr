package parser

import LineColumn
import LineColumnBorders
import SourceInterval

fun SourceInterval.getLineColumnBorders(lineOffsets: List<Int>): LineColumnBorders {
    return LineColumnBorders(offset.getLineColumn(lineOffsets), end().getLineColumn(lineOffsets))
}

fun Int.getLineColumn(lineOffsets: List<Int>): LineColumn {
    lineOffsets.binarySearch { it.compareTo(this) }.let { lineOffset ->
        return if (lineOffset < 0) {
            val line = -lineOffset - 2
            LineColumn(line + 1, this - lineOffsets[line] + 1)
        } else {
            LineColumn(lineOffset + 1, 1)
        }
    }
}

fun LineColumn.getOffset(lineOffsets: List<Int>): Int {
    val lineOffset = line - 1
    if (lineOffset < 0 || lineOffset >= lineOffsets.size) {
        return -1
    }
    val lineStart = lineOffsets[lineOffset]
    return lineStart + column - 1
}

fun CharSequence.getLineOffsets(): List<Int> = getLineOffsetsAndMainLineBreak().lineOffsets

data class LineOffsetsAndMainLineBreak(val lineOffsets: List<Int>, val lineBreak: String)

fun CharSequence.getLineOffsetsAndMainLineBreak(): LineOffsetsAndMainLineBreak {
    val lineOffsets = mutableListOf(0)
    var rnCount = 0
    var nCount = 0
    var rCount = 0

    var index = 0
    while (index < length) {
        when (this[index]) {
            '\r' -> {
                if (index + 1 < length && this[index + 1] == '\n') {
                    rnCount++
                    index += 2
                    lineOffsets.add(index)
                } else {
                    rCount++
                    index++
                    lineOffsets.add(index)
                }
            }
            '\n' -> {
                nCount++
                index++
                lineOffsets.add(index)
            }
            else -> {
                index++
            }
        }
    }

    val mainLineBreak = if (rnCount >= nCount) {
        if (rnCount >= rCount) "\r\n" else "\r"
    } else {
        if (nCount >= rCount) "\n" else "\r"
    }

    return LineOffsetsAndMainLineBreak(lineOffsets, mainLineBreak)
}

private val commonEscapeToLiteralChars = mapOf(
    '\n' to 'n',
    '\r' to 'r',
    '\t' to 't',
    '\b' to 'b',
    '\\' to '\\'
)

val antlrStringEscapeToLiteralChars = commonEscapeToLiteralChars + mapOf('\'' to '\'')
val antlrStringLiteralToEscapeChars = antlrStringEscapeToLiteralChars.reverseEscapingMap()

val antlrCharSetEscapeToLiteralChars = commonEscapeToLiteralChars + mapOf(']' to ']', '-' to '-')
val antlrCharSetLiteralToEscapeChars = antlrCharSetEscapeToLiteralChars.reverseEscapingMap()

val stringEscapeToLiteralChars = commonEscapeToLiteralChars + mapOf('"' to '\"')
val stringLiteralToEscapeChars = stringEscapeToLiteralChars.reverseEscapingMap()

fun Map<Char, Char>.reverseEscapingMap(): Map<Char, Char> {
    return entries.associate { (k, v) -> v to k }
}