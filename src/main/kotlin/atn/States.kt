package atn

import DEFAULT_MODE_NAME
import semantics.Mode
import semantics.Rule

open class State(
    val inTransitions: MutableList<Transition>,
    val outTransitions: MutableList<Transition>,
    val number: Int,
) {
    override fun toString(): String {
        return "s$number"
    }
}

abstract class RootState(
    inTransitions: MutableList<Transition>,
    outTransitions: MutableList<Transition>,
    number: Int,
) : State(inTransitions, outTransitions, number)

class RuleState(
    val rule: Rule,
    outTransitions: MutableList<Transition>,
    number: Int,
) : RootState(mutableListOf(), outTransitions, number) {
    override fun toString(): String {
        return "${rule.ruleNode.idToken.value!!}(${super.toString()})"
    }
}

class ModeState(
    val mode: Mode,
    outTransitions: MutableList<Transition>,
    number: Int,
) : RootState(mutableListOf(), outTransitions, number) {
    override fun toString(): String {
        return "${mode.modeTreeNode?.let { it.idToken.value!! } ?: DEFAULT_MODE_NAME}(${super.toString()})"
    }
}