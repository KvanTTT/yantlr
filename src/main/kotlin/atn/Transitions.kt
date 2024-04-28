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
        return "ε ($source -> $target)"
    }
}

class SetTransition(val set: IntervalSet, source: State, target: State, treeNodes: List<AntlrNode>) : Transition(source, target, treeNodes) {
    override fun toString(): String {
        return "$set ($source -> $target)"
    }
}

class RuleTransition(val rule: Rule, source: State, target: State, treeNodes: List<AntlrNode>) : Transition(source, target, treeNodes)