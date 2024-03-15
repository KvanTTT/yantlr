data class SourceInterval(val offset: Int, val length: Int) {
    fun end() = offset + length

    override fun toString(): String {
        return "[${offset}:${offset + length})"
    }
}