package parser

fun List<Int>.getLineColumn(offset: Int): LineColumn {
    binarySearch { it.compareTo(offset) }.let { lineIndex ->
        return if (lineIndex < 0) {
            val line = -lineIndex - 2
            LineColumn(line + 1, offset - this[line] + 1)
        } else {
            LineColumn(lineIndex + 1, 1)
        }
    }
}

fun LineColumn.getOffset(lineIndexes: List<Int>): Int {
    val lineIndex = line - 1
    if (lineIndex < 0 || lineIndex >= lineIndexes.size) {
        return -1
    }
    val lineStart = lineIndexes[lineIndex]
    return lineStart + column - 1
}

fun String.getLineIndexes(): List<Int> {
    return buildList {
        add(0)
        for (i in this@getLineIndexes.indices) {
            if (this@getLineIndexes[i].let {
                    it == '\r' && i + 1 < length && this@getLineIndexes[i + 1] == '\n' || it == '\n'
                }) {
                add(i + 1)
            }
        }
    }
}