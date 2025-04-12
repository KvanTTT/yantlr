package atn

class AtnVerifier(val checkNoEpsilons: Boolean) {
    fun verify(atn: Atn) {
        fun verify(rootStates: List<RootState>) = rootStates.forEach { verify(it) }

        verify(atn.modeStartStates)
        verify(atn.lexerStartStates)
        verify(atn.parserStartStates)
    }

    private fun verify(rootState: RootState) {
        val visitedStates = mutableSetOf<State>()
        val inferredInTransitionsMap = mutableMapOf<State, MutableList<Transition<*>>>()

        inferredInTransitionsMap[rootState] = mutableListOf()

        fun verifyInternal(currentState: State) {
            if (!visitedStates.add(currentState)) return

            for (outTransition in currentState.outTransitions) {
                if ((outTransition.data as? RealTransitionData)?.antlrNodes?.isEmpty() == true) {
                    throw IllegalStateException("Out-transition $outTransition is not bound to an antlr node")
                }

                val target = outTransition.target

                if (outTransition.source != currentState) {
                    throw IllegalStateException("Source of out-transition does not match the current state: $outTransition")
                }

                if (checkNoEpsilons && outTransition.data is EpsilonTransitionData) {
                    throw IllegalStateException("Epsilon transition found in the ATN: $outTransition")
                }

                inferredInTransitionsMap.getOrPut(target) { mutableListOf() }.add(outTransition)

                verifyInternal(target)
            }
        }

        verifyInternal(rootState)

        for (state in visitedStates) {
            val inferredInTransitions = inferredInTransitionsMap.getValue(state).toSet()
            val actualInTransitions = state.inTransitions.toSet()

            val unexpectedInferredInTransitions = inferredInTransitions - actualInTransitions
            if (unexpectedInferredInTransitions.isNotEmpty()) {
                throw IllegalStateException("Unexpected inferred in-transitions $unexpectedInferredInTransitions for state $state")
            }

            val unexpectedActualInTransitions = actualInTransitions - inferredInTransitions
            if (unexpectedActualInTransitions.isNotEmpty()) {
                throw IllegalStateException("Unexpected actual in-transitions $unexpectedActualInTransitions for state $state")
            }
        }
    }
}