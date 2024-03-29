package parser

import LineColumn

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

fun String.getLineOffsets(): List<Int> {
    return buildList {
        add(0)
        for (i in this@getLineOffsets.indices) {
            if (this@getLineOffsets[i].let {
                    it == '\r' && i + 1 < length && this@getLineOffsets[i + 1] != '\n' || it == '\n'
                }) {
                add(i + 1)
            }
        }
    }
}