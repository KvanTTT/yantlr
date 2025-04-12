package atn

import SortedSet
import parser.AntlrNode
import sortedSetOf

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