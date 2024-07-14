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

sealed class TransitionData(val antlrNodes: List<AntlrNode>)

interface NegationTransitionData {
    val negationNode: ElementNode?
}

class EpsilonTransitionData(antlrNodes: List<AntlrNode>) : TransitionData(antlrNodes) {
    override fun toString(): String = "ε"
}

class IntervalTransitionData(
    val interval: Interval, antlrNodes: List<AntlrNode>, override val negationNode: ElementNode? = null
) : TransitionData(antlrNodes), NegationTransitionData {
    override fun toString(): String {
        return "$interval"
    }
}

class RuleTransitionData(
    val rule: Rule, antlrNodes: List<AntlrNode>, override val negationNode: ElementNode? = null
) : TransitionData(antlrNodes), NegationTransitionData {
    override fun toString(): String {
        return "rule(${rule.name})"
    }
}

class EndTransitionData(val rule: Rule, antlrNodes: List<AntlrNode>) : TransitionData(antlrNodes) {
    override fun toString(): String {
        return "end(${rule.name})"
    }
}