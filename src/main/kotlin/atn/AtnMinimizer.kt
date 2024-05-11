package atn

class AtnMinimizer {
    fun removeEpsilonTransitions(atn: Atn): Atn {
        val newModeStartStates = minimize(atn.modeStartStates)
        val newLexerStartStates = minimize(atn.lexerStartStates)
        val newParserStartStates = minimize(atn.parserStartStates)

        return Atn(newModeStartStates, newLexerStartStates, newParserStartStates)
    }

    fun <T : RootState> minimize(rootStates: List<T>): List<T> {
        return buildList {
            for (rootState in rootStates) {
                removeEpsilonTransitions(rootState)
                add(rootState)
            }
        }
    }

    private fun removeEpsilonTransitions(rootState: RootState) {
        val statesStack = ArrayDeque<State>(0)
        val visitedStates = mutableSetOf<State>()
        statesStack.add(rootState)

        while (statesStack.isNotEmpty()) {
            val currentState = statesStack.removeFirst()

            val inTransitions = currentState.inTransitions

            for (transition in currentState.outTransitions) {
                if (visitedStates.add(transition.target)) {
                    statesStack.add(transition.target)
                }
            }

            var inEpsilonTransitions = inTransitions.filterIsInstance<EpsilonTransition>()
            var allInTransitionsAreEpsilon = inEpsilonTransitions.size == inTransitions.size

            for (inEpsilonTransition in inEpsilonTransitions) {
                for (outTransition in currentState.outTransitions) {
                    outTransition.rebind(
                        inEpsilonTransition,
                        // If all in transitions are epsilon, then remove the current state
                        if (allInTransitionsAreEpsilon) outTransition else null,
                        inEpsilonTransition.source,
                        outTransition.target
                    )
                }
            }
        }
    }

    private fun Transition.rebind(newSourceOldOutTransition: Transition, newTargetOldInTransition: Transition?, newSource: State, newTarget: State) {
        if (this is EpsilonTransition && newSource == newTarget) {
            newSource.outTransitions.remove(newSourceOldOutTransition)
            newTarget.inTransitions.remove(newTargetOldInTransition)
            return
        }

        when (this) {
            is EpsilonTransition -> EpsilonTransition(newSource, newTarget, treeNodes)
            is SetTransition -> SetTransition(set, newSource, newTarget, treeNodes)
            is RuleTransition -> RuleTransition(rule, newSource, newTarget, treeNodes)
            is EndTransition -> EndTransition(rule, newSource, newTarget, treeNodes)
            else -> error("Unknown transition type: ${this@AtnMinimizer}")
        }.also { newTransition ->
            // Change out transitions of previous states and in transition of the new target
            val sourceOutTransitions = newSource.outTransitions
            sourceOutTransitions.indexOf(newSourceOldOutTransition).let {
                if (it == -1) {
                    sourceOutTransitions.add(newTransition)
                } else {
                    sourceOutTransitions[it] = newTransition
                }
            }

            val newTargetInTransitions = newTarget.inTransitions
            newTargetInTransitions.indexOf(newTargetOldInTransition).let {
                if (it == -1) {
                    newTargetInTransitions.add(newTransition)
                } else {
                    newTargetInTransitions[it] = newTransition
                }
            }
        }
    }
}