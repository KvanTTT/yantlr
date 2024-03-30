data class LineColumnBorders(val start: LineColumn, val end: LineColumn) {
    constructor(startLine: Int, startColumn: Int, endLine: Int, endColumn: Int) : this(LineColumn(startLine, startColumn), LineColumn(endLine, endColumn))

    constructor(startLine: Int, startColumn: Int, endColumn: Int) : this(LineColumn(startLine, startColumn), LineColumn(startLine, endColumn))

    override fun toString(): String {
        return if (start == end) {
            start.toString()
        } else {
            "$start..$end"
        }
    }
}