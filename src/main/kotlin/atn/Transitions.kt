package atn

import parser.AntlrNode
import parser.ElementNode
import semantics.Rule

class Transition<T : TransitionData>(val data: T, val source: State, val target: State) {
    val isEnclosed = source === target

    override fun toString(): String {
        return "$data ($source -> $target)"
    }
}

sealed class TransitionData

sealed class RealTransitionData(val antlrNodes: List<AntlrNode>, val negationNodes: List<ElementNode>) : TransitionData()

class EpsilonTransitionData(val antlrNode: AntlrNode) : TransitionData() {
    override fun toString(): String = "ε"
}

class IntervalTransitionData(
    val interval: Interval, antlrNodes: List<AntlrNode>, negationNodes: List<ElementNode> = emptyList()
) : RealTransitionData(antlrNodes, negationNodes) {
    override fun toString(): String {
        return "$interval"
    }
}

class RuleTransitionData(
    val rule: Rule, antlrNodes: List<AntlrNode>, negationNodes: List<ElementNode> = emptyList()
) : RealTransitionData(antlrNodes, negationNodes) {
    override fun toString(): String {
        return "rule(${rule.name})"
    }
}

class EndTransitionData(val rule: Rule) : TransitionData() {
    override fun toString(): String {
        return "end(${rule.name})"
    }
}

fun Transition<*>.getNegationNodes(): List<ElementNode>? {
    return if (data is RealTransitionData && data.negationNodes.isNotEmpty()) {
        data.negationNodes
    } else {
        null
    }
}