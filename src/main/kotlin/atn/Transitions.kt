package atn

import Diagnostic
import parser.AntlrNode
import semantics.Rule

sealed class Transition(val source: State, val target: State, val treeNodes: LinkedHashSet<AntlrNode>) {
    val isLoop = source === target

    override fun toString(): String {
        return "$source -> $target"
    }
}

class EpsilonTransition(source: State, target: State, treeNodes: LinkedHashSet<AntlrNode>) : Transition(source, target, treeNodes) {
    override fun toString(): String {
        return "ε (${super.toString()})"
    }
}

class SetTransition(val set: IntervalSet, source: State, target: State, treeNodes: LinkedHashSet<AntlrNode>) : Transition(source, target, treeNodes) {
    override fun toString(): String {
        return "$set (${super.toString()})"
    }
}

class RuleTransition(val rule: Rule, source: State, target: State, treeNodes: LinkedHashSet<AntlrNode>) : Transition(source, target, treeNodes) {
    override fun toString(): String {
        return "$rule (${super.toString()})"
    }
}

class EndTransition(val rule: Rule, source: State, target: State, treeNodes: LinkedHashSet<AntlrNode>) : Transition(source, target, treeNodes) {
    override fun toString(): String {
        return "end ($rule, ${super.toString()})"
    }
}

class ErrorTransition(val diagnostics: LinkedHashSet<Diagnostic>, source: State, target: State, treeNodes: LinkedHashSet<AntlrNode>) : Transition(source, target, treeNodes) {
    override fun toString(): String {
        return "error (${diagnostics.joinToString(",") { it::class.simpleName as String }} ${super.toString()})"
    }
}