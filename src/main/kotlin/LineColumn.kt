data class LineColumn(val line: Int, val column: Int) {
    override fun toString(): String {
        return "$line:$column"
    }
}