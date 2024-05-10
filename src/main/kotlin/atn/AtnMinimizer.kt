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
                refineRoot(rootState)
                removeEpsilonTransitions(rootState)
                add(rootState)
            }
        }
    }

    private fun refineRoot(rootState: RootState) {
        do {
            val epsilonTransitions = rootState.outTransitions.filterIsInstance<EpsilonTransition>()
                .takeIf { it.isNotEmpty() } ?: break

            for (epsilonTransition in epsilonTransitions) {
                val target = epsilonTransition.target
                val targetOutTransitions = target.outTransitions
                // Check for cycling
                if (target != rootState) {
                    // Remove intermediate target state and rebind all its targets
                    for (targetOutTransition in targetOutTransitions) {
                        targetOutTransition.rebind(epsilonTransition, targetOutTransition, rootState, targetOutTransition.target)
                    }
                    // Rebind incoming transitions to the ruleState
                    for (targetInTransition in target.inTransitions) {
                        if (targetInTransition != epsilonTransition) {
                            targetInTransition.rebind(targetInTransition.source, rootState)
                        }
                    }
                    if (targetOutTransitions.isNotEmpty()) {
                        targetOutTransitions.clear()
                    } else {
                        // If target state is the end state, then just remove it
                        rootState.outTransitions.clear()
                    }
                } else {
                    rootState.outTransitions.remove(epsilonTransition)
                }
                epsilonTransition.target.inTransitions.remove(epsilonTransition)
            }
        } while (true)
    }

    private fun removeEpsilonTransitions(rootState: RootState) {
        val statesStack = ArrayDeque<State>(0)
        val visitedStates = mutableSetOf<State>()
        statesStack.add(rootState)

        while (statesStack.isNotEmpty()) {
            val currentState = statesStack.removeFirst()
            if (!visitedStates.add(currentState)) continue

            val outTransitions = currentState.outTransitions
            for (transition in outTransitions) {
                statesStack.add(transition.target)
            }

            // The previous transitions are supposed to be non epsilons

            val epsilonTransitions = outTransitions.filterIsInstance<EpsilonTransition>()
            val allTransitionsAreEpsilon = epsilonTransitions.size == outTransitions.size

            for (epsilonTransition in epsilonTransitions) {
                for (inTransition in currentState.inTransitions) {
                    // If all transitions are epsilon, then remove the `epsilonTransition.target` state
                    val newSourceOldOutTransition = if (allTransitionsAreEpsilon) inTransition else null
                    inTransition.rebind(
                        newSourceOldOutTransition,
                        epsilonTransition,
                        inTransition.source,
                        epsilonTransition.target
                    )
                }
                outTransitions.remove(epsilonTransition)
            }
        }
    }

    private fun Transition.rebind(newSource: State, newTarget: State): Transition {
        return this.rebind(this, this, newSource, newTarget)
    }

    private fun Transition.rebind(newSourceOldOutTransition: Transition?, newTargetOldInTransition: Transition?, newSource: State, newTarget: State): Transition {
        return when (this) {
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