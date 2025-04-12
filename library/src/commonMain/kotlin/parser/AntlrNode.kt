package parser

import SourceInterval

abstract class AntlrNode : Comparable<AntlrNode> {
    abstract fun getInterval(): SourceInterval

    override fun compareTo(other: AntlrNode): Int {
        return getInterval().end() - other.getInterval().end()
    }
}