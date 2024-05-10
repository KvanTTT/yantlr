package atn

import parser.AntlrNode
import semantics.Rule

abstract class Transition(val source: State, val target: State, val treeNodes: List<AntlrNode>) {
    override fun toString(): String {
        return "$source -> $target"
    }
}

class EpsilonTransition(source: State, target: State, treeNodes: List<AntlrNode>) : Transition(source, target, treeNodes) {
    override fun toString(): String {
        return "ε (${super.toString()})"
    }
}

class SetTransition(val set: IntervalSet, source: State, target: State, treeNodes: List<AntlrNode>) : Transition(source, target, treeNodes) {
    override fun toString(): String {
        return "$set (${super.toString()})"
    }
}

class RuleTransition(val rule: Rule, source: State, target: State, treeNodes: List<AntlrNode>) : Transition(source, target, treeNodes) {
    override fun toString(): String {
        return "$rule (${super.toString()})"
    }
}

class EndTransition(val rule: Rule, source: State, target: State, treeNodes: List<AntlrNode>) : Transition(source, target, treeNodes) {
    override fun toString(): String {
        return "end ($rule, ${super.toString()})"
    }
}