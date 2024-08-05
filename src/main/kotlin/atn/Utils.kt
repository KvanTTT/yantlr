package atn

import parser.AntlrNode
import parser.ElementNode
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

fun Transition<*>.getNegationNodes(): List<ElementNode>? {
    return if (data is RealTransitionData && data.negationNodes.isNotEmpty()) {
        data.negationNodes
    } else {
        null
    }
}

fun Transition<*>.getNonGreedyNodes(): List<ElementNode>? {
    return if (data is RealTransitionData && data.nonGreedyNodes.isNotEmpty()) {
        data.nonGreedyNodes
    } else {
        null
    }
}