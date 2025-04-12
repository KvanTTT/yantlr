package atn

import SortedSet
import parser.AntlrNode
import parser.ElementBody
import declarations.Rule

class Transition<T : TransitionData>(val data: T, val source: State, val target: State) {
    val isEnclosed = source === target

    override fun toString(): String {
        return "$data ($source -> $target)"
    }
}

sealed class TransitionData

sealed class RealTransitionData(val antlrNodes: SortedSet<AntlrNode>, val negationNodes: List<ElementBody>) : TransitionData()

class EpsilonTransitionData(val antlrNode: AntlrNode) : TransitionData() {
    override fun toString(): String = "ε"
}

class IntervalTransitionData(
    val interval: Interval, antlrNodes: SortedSet<AntlrNode>, negationNodes: List<ElementBody> = emptyList()
) : RealTransitionData(antlrNodes, negationNodes) {
    override fun toString(): String {
        return "$interval"
    }
}

class RuleTransitionData(
    val rule: Rule, antlrNodes: SortedSet<AntlrNode>, negationNodes: List<ElementBody> = emptyList()
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

fun Transition<*>.getNegationNodes(): List<ElementBody>? {
    return if (data is RealTransitionData && data.negationNodes.isNotEmpty()) {
        data.negationNodes
    } else {
        null
    }
}