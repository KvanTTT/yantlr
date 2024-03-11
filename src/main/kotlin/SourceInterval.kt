data class SourceInterval(val offset: Int, val length: Int) {
    override fun toString(): String {
        return "[${offset}:${offset + length})"
    }
}