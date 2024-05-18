package atn

private typealias TransitionReplacementMap = MutableMap<State, MutableMap<Transition, MutableList<Transition>>>

object AtnMinimizer {
    fun removeEpsilonTransitions(atn: Atn) {
        fun <T : RootState> minimize(rootStates: List<T>) = rootStates.forEach { rootState -> removeEpsilonTransitions(rootState) }

        minimize(atn.modeStartStates)
        minimize(atn.lexerStartStates)
        minimize(atn.parserStartStates)
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
            val inEpsilonTransitions = inTransitions.filterIsInstance<EpsilonTransition>()

            if (inEpsilonTransitions.isNotEmpty()) {
                // If all incoming transitions are epsilon, then the current state is effectively unreachable.
                // Thus, it can be removed
                val preserveCurrentState = inEpsilonTransitions.size != inTransitions.size

                val newSourceOutTransitionsMap: TransitionReplacementMap = mutableMapOf()
                val newTargetInTransitionsMap: TransitionReplacementMap = mutableMapOf()

                for (inEpsilonTransition in inEpsilonTransitions) {
                    // Always remove all incoming epsilon transitions
                    newTargetInTransitionsMap.addForRemoving(inEpsilonTransition.target, inEpsilonTransition)

                    if (inEpsilonTransition.source == inEpsilonTransition.target) {
                        // Remove self-loop epsilon transition
                        newSourceOutTransitionsMap.addForRemoving(inEpsilonTransition.source, inEpsilonTransition)
                    } else {
                        val newOutTransitions = currentState.cloneNonExistingOutTransitions(inEpsilonTransition.source)

                        newSourceOutTransitionsMap.addReplacement(
                            inEpsilonTransition.source,
                            inEpsilonTransition,
                            newOutTransitions.values,
                            preserveOldTransition = false
                        )

                        for ((oldTransition, newTransition) in newOutTransitions) {
                            newTargetInTransitionsMap.addReplacement(
                                oldTransition.target,
                                oldTransition,
                                listOf(newTransition),
                                preserveOldTransition = preserveCurrentState
                            )
                        }
                    }
                }

                newSourceOutTransitionsMap.rebuildTransitions(isOutTransitions = true)
                newTargetInTransitionsMap.rebuildTransitions(isOutTransitions = false)
            }
        }
    }

    private fun State.cloneNonExistingOutTransitions(newSource: State): Map<Transition, Transition> {
        val result = LinkedHashMap<Transition, Transition>()
        for (oldOutTransition in outTransitions) {
            // Filter out enclosed epsilon transitions
            if (oldOutTransition is EpsilonTransition && oldOutTransition.source === oldOutTransition.target) continue

            // Don't add a new transition if it already exists
            if (newSource.outTransitions.none {
                    it.checkExistingByInfo(oldOutTransition) || it.target === oldOutTransition.target
                }
            ) {
                result[oldOutTransition] = oldOutTransition.clone(newSource, oldOutTransition.target)
            }
        }
        return result
    }

    private fun TransitionReplacementMap.addForRemoving(state: State, oldTransition: Transition) {
        addReplacement(state, oldTransition, emptyList(), preserveOldTransition = false)
    }

    private fun TransitionReplacementMap.addReplacement(
        state: State,
        oldTransition: Transition,
        addingTransitions: Collection<Transition>,
        preserveOldTransition: Boolean
    ) {
        val newTransitions = getOrPut(state) { mutableMapOf() }.getOrPut(oldTransition) {
            mutableListOf<Transition>().also { if (preserveOldTransition) it.add(oldTransition) }
        }
        newTransitions.addAll(addingTransitions)
    }

    private fun TransitionReplacementMap.rebuildTransitions(isOutTransitions: Boolean) {
        for ((state, transitionReplacement) in this) {
            val oldTransitions = if (isOutTransitions) state.outTransitions else state.inTransitions
            val newTransitions = buildList {
                for (oldTransition in oldTransitions) {
                    val replacement = transitionReplacement[oldTransition]
                    if (replacement != null) {
                        replacement.forEach { newTransition -> add(newTransition) }
                    } else {
                        add(oldTransition)
                    }
                }
            }
            oldTransitions.clear()
            oldTransitions.addAll(newTransitions)
        }
    }

    private fun Transition.checkExistingByInfo(other: Transition): Boolean {
        when (this) {
            is EpsilonTransition -> {
                if (other !is EpsilonTransition) return false
            }
            is SetTransition -> {
                if (other !is SetTransition || set !== other.set) return false
            }
            is RuleTransition -> {
                if (other !is RuleTransition || rule !== other.rule) return false
            }
            is EndTransition -> {
                if (other !is EndTransition || rule !== other.rule) return false
            }
            else -> error("Unknown transition type: ${this@AtnMinimizer}")
        }

        return treeNodes === other.treeNodes
    }
}