data class LineColumnInterval(val start: LineColumn, val end: LineColumn) {
    override fun toString(): String {
        return if (start == end) {
            start.toString()
        } else {
            "$start..$end"
        }
    }
}