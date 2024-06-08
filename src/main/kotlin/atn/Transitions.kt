package atn

import Diagnostic
import parser.AntlrNode
import semantics.Rule

sealed class Transition(val source: State, val target: State, val treeNodes: LinkedHashSet<AntlrNode>) {
    val isLoop = source === target

    override fun toString(): String {
        return "$source -> $target"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other !is Transition ||
            source !== other.source ||
            target !== other.target ||
            !treeNodes.containsAll(other.treeNodes)
        ) {
            return false
        }

        when (this) {
            is EpsilonTransition -> {
                if (other !is EpsilonTransition) return false
            }
            is SetTransition -> {
                if (other !is SetTransition || set != other.set) return false
            }
            is RuleTransition -> {
                if (other !is RuleTransition || rule !== other.rule) return false
            }
            is EndTransition -> {
                if (other !is EndTransition || rule !== other.rule) return false
            }
            is ErrorTransition -> {
                if (other !is ErrorTransition || !diagnostics.containsAll(other.diagnostics)) return false
            }
        }

        return true
    }

    override fun hashCode(): Int {
        var result = this::class.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + target.hashCode()
        result = 31 * result + treeNodes.hashCode()

        when (this) {
            is EpsilonTransition -> {
            }
            is SetTransition -> {
                result = 31 * result + set.hashCode()
            }
            is RuleTransition -> {
                result = 31 * result + rule.hashCode()
            }
            is EndTransition -> {
                result = 31 * result + rule.hashCode()
            }
            is ErrorTransition -> {
                result = 31 * result + diagnostics.hashCode()
            }
        }

        return result
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