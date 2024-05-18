package atn

object AtnCloner {
    fun clone(atn: Atn): Atn {
        fun <T : State> List<T>.clone() = map { clone(it) }

        val modeStartStates = atn.modeStartStates.clone()
        val lexerStartStates = atn.lexerStartStates.clone()
        val parserStartStates = atn.parserStartStates.clone()
        return Atn(modeStartStates, lexerStartStates, parserStartStates)
    }

    fun <T : State> clone(state: T): T {
        @Suppress("UNCHECKED_CAST")
        return Helper().clone(state) as T
    }

    fun <T: State> cloneWithMap(state: T): Pair<T, StateTransitionMap> {
        val helper = Helper()
        val clonedState = helper.clone(state)
        @Suppress("UNCHECKED_CAST")
        return (clonedState as T) to helper.getStateTransitionMap()
    }

    private class Helper {
        val statesMap: MutableMap<State, State> = mutableMapOf()
        val transitionsMap: MutableMap<Transition, Transition> = mutableMapOf()

        fun getStateTransitionMap(): StateTransitionMap {
            return StateTransitionMap(statesMap, transitionsMap)
        }

        fun clone(state: State): State {
            createNewStates(state)
            createNewTransitions()
            return statesMap.getValue(state)
        }

        private fun createNewStates(state: State): State {
            statesMap[state]?.let { return it }

            val result = when (state) {
                is RuleState -> RuleState(state.rule, mutableListOf(), state.number)
                is ModeState -> ModeState(state.mode, mutableListOf(), state.number)
                else -> State(mutableListOf(), mutableListOf(), state.number)
            }.also {
                statesMap[state] = it
            }

            for (transition in state.outTransitions) {
                createNewStates(transition.target)
            }

            return result
        }

        private fun createNewTransitions() {
            for ((oldState, newState) in statesMap) {
                newState.inTransitions.cloneTransitionsFrom(oldState.inTransitions)
                newState.outTransitions.cloneTransitionsFrom(oldState.outTransitions)
            }
        }

        private fun MutableList<Transition>.cloneTransitionsFrom(transitions: List<Transition>) {
            for (transition in transitions) {
                add(transition.clone())
            }
        }

        private fun Transition.clone(): Transition {
            transitionsMap[this]?.let { return it }

            val newSource = statesMap.getValue(source)
            val newTarget = statesMap.getValue(target)
            return clone(newSource, newTarget).also { transitionsMap[this] = it }
        }
    }
}

data class StateTransitionMap(val states: Map<State, State>, val transitions: Map<Transition, Transition>)