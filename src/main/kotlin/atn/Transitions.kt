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

sealed interface RealTransitionData {
    val negationNodes: List<ElementNode>
    val antlrNodes: List<AntlrNode>
}

class EpsilonTransitionData(val antlrNode: AntlrNode) : TransitionData() {
    override fun toString(): String = "ε"
}

class IntervalTransitionData(
    val interval: Interval, override val antlrNodes: List<AntlrNode>, override val negationNodes: List<ElementNode> = emptyList()
) : TransitionData(), RealTransitionData {
    override fun toString(): String {
        return "$interval"
    }
}

class RuleTransitionData(
    val rule: Rule, override val antlrNodes: List<AntlrNode>, override val negationNodes: List<ElementNode> = emptyList()
) : TransitionData(), RealTransitionData {
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