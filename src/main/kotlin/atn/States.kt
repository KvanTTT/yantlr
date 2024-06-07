package atn

import DEFAULT_MODE_NAME
import semantics.Mode
import semantics.Rule

open class State(
    val inTransitions: LinkedHashSet<Transition>,
    val outTransitions: LinkedHashSet<Transition>,
    val number: Int,
) {
    override fun toString(): String {
        return "s$number"
    }
}

abstract class RootState(
    inTransitions: LinkedHashSet<Transition>,
    outTransitions: LinkedHashSet<Transition>,
    number: Int,
) : State(inTransitions, outTransitions, number)

class RuleState(
    val rule: Rule,
    outTransitions: LinkedHashSet<Transition>,
    number: Int,
) : RootState(LinkedHashSet(), outTransitions, number) {
    override fun toString(): String {
        return "${rule.ruleNode.idToken.value!!}(${super.toString()})"
    }
}

class ModeState(
    val mode: Mode,
    outTransitions: LinkedHashSet<Transition>,
    number: Int,
) : RootState(LinkedHashSet(), outTransitions, number) {
    override fun toString(): String {
        return "${mode.modeTreeNode.modeDeclaration?.let { it.idToken.value!! } ?: DEFAULT_MODE_NAME}(${super.toString()})"
    }
}