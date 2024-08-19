package atn

import DEFAULT_MODE_NAME
import types.ModeType
import types.RuleType

open class State(val number: Int) {
    val inTransitions: MutableList<Transition<*>> = mutableListOf()
    val outTransitions: MutableList<Transition<*>> = mutableListOf()

    override fun toString(): String {
        return "s$number"
    }
}

abstract class RootState(number: Int) : State(number)

class RuleState(val ruleType: RuleType, number: Int) : RootState(number) {
    override fun toString(): String {
        return "${ruleType.name}(${super.toString()})"
    }
}

class ModeState(val modeType: ModeType, number: Int) : RootState(number) {
    override fun toString(): String {
        return "${modeType.name ?: DEFAULT_MODE_NAME}(${super.toString()})"
    }
}