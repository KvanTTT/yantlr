package atn

import parser.AntlrTreeNode
import semantics.Rule

open class State(
    val inTransitions: MutableList<Transition>,
    val outTransitions: MutableList<Transition>,
    val number: Int,
) {
    fun isStartState() = inTransitions.isEmpty()

    fun isEndState() = outTransitions.isEmpty()

    override fun toString(): String {
        return "s$number"
    }
}

class RuleState(
    val rule: Rule,
    val treeNode: AntlrTreeNode,
    outTransitions: MutableList<Transition>,
    number: Int,
) : State(mutableListOf(), outTransitions, number) {
    override fun toString(): String {
        return "${rule.name}(${super.toString()})"
    }
}