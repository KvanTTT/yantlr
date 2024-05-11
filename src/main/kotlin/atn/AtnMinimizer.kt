package atn

object AtnMinimizer {
    fun removeEpsilonTransitions(atn: Atn) {
        minimize(atn.modeStartStates)
        minimize(atn.lexerStartStates)
        minimize(atn.parserStartStates)
    }

    fun <T : RootState> minimize(rootStates: List<T>) {
        for (rootState in rootStates) {
            removeEpsilonTransitions(rootState)
        }
    }

    private fun removeEpsilonTransitions(rootState: RootState) {
        val statesStack = ArrayDeque<State>(0)
        val visitedStates = mutableSetOf<State>()
        statesStack.add(rootState)

        while (statesStack.isNotEmpty()) {
            val currentState = statesStack.removeFirst()

            for (transition in currentState.outTransitions) {
                if (visitedStates.add(transition.target)) {
                    statesStack.add(transition.target)
                }
            }

            val inTransitions = currentState.inTransitions
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
            // Change out transitions of previous state and in transition of the new target
            newSource.outTransitions.processTransitions(newSourceOldOutTransition, newTransition)
            newTarget.inTransitions.processTransitions(newTargetOldInTransition, newTransition)
        }
    }

    private fun MutableList<Transition>.processTransitions(oldTransition: Transition?, newTransition: Transition) {
        indexOf(oldTransition).let { indexOfOld ->
            val indexOfExisting = indexOfFirst { it.checkExisting(newTransition) }
            if (indexOfOld == -1) {
                if (indexOfExisting == -1) {
                    add(newTransition)
                }
            } else {
                if (indexOfExisting == -1) {
                    this[indexOfOld] = newTransition
                } else {
                    removeAt(indexOfOld)
                }
            }
        }
    }

    private fun Transition.checkExisting(other: Transition): Boolean {
        when (this) {
            is EpsilonTransition -> {
                if (other !is EpsilonTransition) return false
            }
            is SetTransition -> {
                if (other !is SetTransition || set != other.set) return false
            }
            is RuleTransition -> {
                if (other !is RuleTransition || rule != other.rule) return false
            }
            is EndTransition -> {
                if (other !is EndTransition || rule != other.rule) return false
            }
            else -> error("Unknown transition type: ${this@AtnMinimizer}")
        }

        return source == other.source && target == other.target && treeNodes == other.treeNodes
    }
}