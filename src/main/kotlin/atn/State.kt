package atn

import parser.AntlrTreeNode
import semantics.Rule

open class State(val transitions: List<Transition>, val number: Int) {
    override fun toString(): String {
        return "s$number"
    }
}

class RuleState(
    val rule: Rule,
    val treeNode: AntlrTreeNode,
    val startState: State,
    val endState: State,
    number: Int,
) : State(startState.transitions, number) {
    override fun toString(): String {
        return "${rule.name}(${super.toString()})"
    }
}