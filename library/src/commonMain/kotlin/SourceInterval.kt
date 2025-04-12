import parser.AntlrNode
import java.util.SortedSet

data class SourceInterval(val offset: Int, val length: Int) {
    companion object {
        val EMPTY = SourceInterval(0, 0)
    }

    fun end() = offset + length

    override fun toString(): String {
        return "[${offset}:${offset + length})"
    }
}

fun SortedSet<AntlrNode>.merge(): SourceInterval {
    if (isEmpty()) {
        return SourceInterval.EMPTY
    }

    var minStart = Int.MAX_VALUE
    var maxEnd = Int.MIN_VALUE

    for (node in this) {
        val interval = node.getInterval()
        if (interval.offset < minStart) {
            minStart = interval.offset
        }
        val intervalEnd = interval.end()
        if (intervalEnd > maxEnd) {
            maxEnd = intervalEnd
        }
    }

    return SourceInterval(minStart, maxEnd - minStart)
}