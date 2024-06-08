package atn

import Diagnostic
import parser.AntlrNode
import semantics.Rule

sealed class Transition(val source: State, val target: State, val treeNodes: LinkedHashSet<AntlrNode>) {
    val isLoop = source == target

    override fun toString(): String {
        return "$source -> $target"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Transition && source === other.source && target === other.target && treeNodes === other.treeNodes
    }

    override fun hashCode(): Int {
        var result = this::class.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + target.hashCode()
        return result
    }
}

class EpsilonTransition(source: State, target: State, treeNodes: LinkedHashSet<AntlrNode>) : Transition(source, target, treeNodes) {
    override fun toString(): String {
        return "ε (${super.toString()})"
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other) && other is EpsilonTransition
    }

    override fun hashCode(): Int {
        return super.hashCode()
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

    override fun equals(other: Any?): Boolean {
        return super.equals(other) && other is RuleTransition && rule === other.rule
    }

    override fun hashCode(): Int {
        return super.hashCode() + 31 * rule.hashCode()
    }
}

class EndTransition(val rule: Rule, source: State, target: State, treeNodes: LinkedHashSet<AntlrNode>) : Transition(source, target, treeNodes) {
    override fun toString(): String {
        return "end ($rule, ${super.toString()})"
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other) && other is EndTransition && rule === other.rule
    }

    override fun hashCode(): Int {
        return super.hashCode() + 31 * rule.hashCode()
    }
}

class ErrorTransition(val diagnostics: LinkedHashSet<Diagnostic>, source: State, target: State, treeNodes: LinkedHashSet<AntlrNode>) : Transition(source, target, treeNodes) {
    override fun toString(): String {
        return "error (${diagnostics.joinToString(",") { it::class.simpleName as String }} ${super.toString()})"
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other) && other is ErrorTransition
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}