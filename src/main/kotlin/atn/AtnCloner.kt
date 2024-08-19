package atn

object AtnCloner {
    fun clone(atn: Atn): Atn {
        @Suppress("UNCHECKED_CAST")
        fun <T : State> List<T>.clone() = map { Helper(stateCounter = null).clone(it) as T }

        val modeStartStates = atn.modeStartStates.clone()
        val lexerStartStates = atn.lexerStartStates.clone()
        val parserStartStates = atn.parserStartStates.clone()
        return Atn(modeStartStates, lexerStartStates, parserStartStates, atn.stateCounter)
    }

    fun <T: State> clone(state: T, stateCounter: Int): CloneInfo {
        val helper = Helper(stateCounter)
        helper.clone(state)
        return helper.getCloneInfo()
    }

    private class Helper(var stateCounter: Int?) {
        val statesMap: MutableMap<State, State> = mutableMapOf()

        fun getCloneInfo(): CloneInfo {
            return CloneInfo(stateCounter!!, statesMap)
        }

        fun clone(state: State): State {
            createNewStates(state)
            createNewTransitions()
            return statesMap.getValue(state)
        }

        private fun createNewStates(state: State): State {
            statesMap[state]?.let { return it }

            val number = if (stateCounter == null) state.number else stateCounter!!
            val result = when (state) {
                is RuleState -> RuleState(state.ruleType, number)
                is ModeState -> ModeState(state.mode, number)
                else -> State(number)
            }.also {
                statesMap[state] = it
            }
            if (stateCounter != null) stateCounter = number + 1

            for (transition in state.outTransitions) {
                createNewStates(transition.target)
            }

            return result
        }

        private fun createNewTransitions() {
            for ((oldState, _) in statesMap) {
                for (transition in oldState.outTransitions) {
                    transition.data.bind(statesMap.getValue(transition.source), statesMap.getValue(transition.target))
                }
            }
        }
    }
}

class CloneInfo(val stateCounter: Int, private val states: Map<State, State>) {
    fun getMappedState(state: State): State = states.getValue(state)
}