package atn

import DEFAULT_MODE_NAME
import semantics.Mode
import semantics.Rule

open class State(val number: Int) {
    val inTransitions: LinkedHashSet<Transition> = LinkedHashSet()
    val outTransitions: LinkedHashSet<Transition> = LinkedHashSet()

    override fun toString(): String {
        return "s$number"
    }
}

abstract class RootState(number: Int) : State(number)

class RuleState(val rule: Rule, number: Int) : RootState(number) {
    override fun toString(): String {
        return "${rule.ruleNode.idToken.value!!}(${super.toString()})"
    }
}

class ModeState(val mode: Mode, number: Int) : RootState(number) {
    override fun toString(): String {
        return "${mode.modeTreeNode.modeDeclaration?.let { it.idToken.value!! } ?: DEFAULT_MODE_NAME}(${super.toString()})"
    }
}