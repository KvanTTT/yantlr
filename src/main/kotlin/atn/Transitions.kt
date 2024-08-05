package atn

import parser.AntlrNode
import parser.ElementNode
import semantics.Rule
import java.util.SortedSet

class Transition<T : TransitionData>(val data: T, val source: State, val target: State) {
    val isEnclosed = source === target

    override fun toString(): String {
        return "$data ($source -> $target)"
    }
}

sealed class TransitionData

sealed class RealTransitionData(
    val antlrNodes: SortedSet<AntlrNode>,
    val negationNodes: List<ElementNode>,
    val nonGreedyNodes: List<ElementNode>,
) : TransitionData()

class EpsilonTransitionData(val antlrNode: AntlrNode) : TransitionData() {
    override fun toString(): String = "ε"
}

class IntervalTransitionData(
    val interval: Interval, antlrNodes: SortedSet<AntlrNode>,
    negationNodes: List<ElementNode> = emptyList(),
    nonGreedyNodes: List<ElementNode> = emptyList(),
) : RealTransitionData(antlrNodes, negationNodes, nonGreedyNodes) {
    override fun toString(): String {
        return "$interval"
    }
}

class RuleTransitionData(
    val rule: Rule, antlrNodes: SortedSet<AntlrNode>,
    negationNodes: List<ElementNode> = emptyList(),
    nonGreedyNodes: List<ElementNode> = emptyList(),
) : RealTransitionData(antlrNodes, negationNodes, nonGreedyNodes) {
    override fun toString(): String {
        return "rule(${rule.name})"
    }
}

class EndTransitionData(val rule: Rule) : TransitionData() {
    override fun toString(): String {
        return "end(${rule.name})"
    }
}
