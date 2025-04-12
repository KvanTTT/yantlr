data class LineColumnBorders(val start: LineColumn, val end: LineColumn) {
    constructor(startLine: Int, startColumn: Int, endLine: Int, endColumn: Int) : this(LineColumn(startLine, startColumn), LineColumn(endLine, endColumn))

    constructor(startLine: Int, startColumn: Int, endColumn: Int) : this(LineColumn(startLine, startColumn), LineColumn(startLine, endColumn))

    constructor(startLine: Int, startColumn: Int) : this(LineColumn(startLine, startColumn), LineColumn(startLine, startColumn))

    override fun toString(): String {
        return when {
            start == end -> start.toString()
            start.line == end.line -> "${start.line}:${start.column}..${end.column}"
            else -> "$start..$end"
        }
    }
}