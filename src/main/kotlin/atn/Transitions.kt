package atn

import SemanticsDiagnostic
import parser.AntlrNode
import semantics.Rule

class Transition<T : TransitionData>(val data: T, val source: State, val target: State) {
    val isEnclosed = source === target

    override fun toString(): String {
        return "$data ($source -> $target)"
    }
}

sealed class TransitionData(val antlrNodes: List<AntlrNode>)

class EpsilonTransitionData(antlrNodes: List<AntlrNode>) : TransitionData(antlrNodes) {
    override fun toString(): String = "ε"
}

class IntervalTransitionData(val interval: Interval, antlrNodes: List<AntlrNode>) : TransitionData(antlrNodes) {
    override fun toString(): String {
        return "$interval"
    }
}

class SetTransitionData(val set: IntervalSet, antlrNodes: List<AntlrNode>) : TransitionData(antlrNodes) {
    override fun toString(): String {
        return "$set"
    }
}

class RuleTransitionData(val rule: Rule, antlrNodes: List<AntlrNode>) : TransitionData(antlrNodes) {
    override fun toString(): String {
        return "rule(${rule.name})"
    }
}

class EndTransitionData(val rule: Rule, antlrNodes: List<AntlrNode>) : TransitionData(antlrNodes) {
    override fun toString(): String {
        return "end(${rule.name})"
    }
}