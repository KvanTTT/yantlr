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
        val visitedStates = mutableSetOf<State>()

        fun removeEpsilonTransitionsInternal(currentState: State) {
            if (!visitedStates.add(currentState)) return

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

                    // Enclosed epsilon transitions should not be created during epsilons removing
                    require(inEpsilonTransition.source != inEpsilonTransition.target)

                    val newOutTransitions =
                        currentState.cloneNonExistingAndValidOutTransitions(inEpsilonTransition)

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

                newSourceOutTransitionsMap.rebuildTransitions(isOutTransitions = true)
                newTargetInTransitionsMap.rebuildTransitions(isOutTransitions = false)
            }

            for (outTarget in currentState.outTransitions.map { it.target }) {
                removeEpsilonTransitionsInternal(outTarget)
            }
        }

        removeEpsilonTransitionsInternal(rootState)
    }

    private fun State.cloneNonExistingAndValidOutTransitions(inTransition: Transition): Map<Transition, Transition> {
        val newSource = inTransition.source
        val result = LinkedHashMap<Transition, Transition>()
        for (oldOutTransition in outTransitions) {
            // Enclosed epsilon transitions should not be created during epsilons removing
            require(oldOutTransition !is EpsilonTransition || oldOutTransition.source !== oldOutTransition.target)

            fun isEnclosedEpsilonTransition() = oldOutTransition is EpsilonTransition && newSource == oldOutTransition.target
            fun isNewTransitionAlreadyPresented() = newSource.outTransitions.any {
                it.checkExistingByInfo(oldOutTransition) || it.target === oldOutTransition.target
            }

            if (!isEnclosedEpsilonTransition() && !isNewTransitionAlreadyPresented()) {
                val newTreeNodes = if (oldOutTransition is EndTransition) {
                    if (newSource is RootState) {
                        // root states don't have incoming transitions -> trying to extract tree nodes from the in-transition
                        inTransition.treeNodes
                    } else {
                        // Extract nodes from the previous source's in-transitions and skip removing epsilons
                        newSource.inTransitions.filter { it !is EpsilonTransition }.flatMap { it.treeNodes }.distinct()
                    }
                } else {
                    oldOutTransition.treeNodes
                }
                result[oldOutTransition] = oldOutTransition.clone(newSource, oldOutTransition.target, newTreeNodes)
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