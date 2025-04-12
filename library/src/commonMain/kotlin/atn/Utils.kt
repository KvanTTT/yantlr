package atn

import parser.AntlrNode
import java.util.*

fun Transition<*>.getAntlrNodes(): SortedSet<AntlrNode> {
    return when (data) {
        is EpsilonTransitionData -> {
            sortedSetOf(data.antlrNode)
        }

        is RealTransitionData -> {
            data.antlrNodes
        }

        is EndTransitionData -> {
            sortedSetOf(data.rule.treeNode)
        }
    }
}