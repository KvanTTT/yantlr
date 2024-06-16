package atn

import DEFAULT_MODE_NAME
import semantics.Mode
import semantics.Rule

open class State(val number: Int) {
    val inTransitions: MutableList<Transition<*>> = mutableListOf()
    val outTransitions: MutableList<Transition<*>> = mutableListOf()

    override fun toString(): String {
        return "s$number"
    }
}

abstract class RootState(number: Int) : State(number)

class RuleState(val rule: Rule, number: Int) : RootState(number) {
    override fun toString(): String {
        return "${rule.name}(${super.toString()})"
    }
}

class ModeState(val mode: Mode, number: Int) : RootState(number) {
    override fun toString(): String {
        return "${mode.modeTreeNode.modeDeclaration?.let { it.idToken.value!! } ?: DEFAULT_MODE_NAME}(${super.toString()})"
    }
}