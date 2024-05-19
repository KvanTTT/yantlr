package atn

private typealias TransitionReplacementMap = MutableMap<State, MutableMap<Transition, MutableList<Transition>>>

object AtnEpsilonRemover {
    fun run(atn: Atn) {
        fun <T : RootState> run(rootStates: List<T>) = rootStates.forEach { rootState -> run(rootState) }

        run(atn.modeStartStates)
        run(atn.lexerStartStates)
        run(atn.parserStartStates)
    }

    private fun run(rootState: RootState) {
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

                    val transitionsReplacement = currentState.getReplacement(inEpsilonTransition)

                    newSourceOutTransitionsMap.addReplacement(
                        inEpsilonTransition.source,
                        inEpsilonTransition,
                        transitionsReplacement.values.filterNotNull(),
                        preserveOldTransition = false
                    )

                    for ((oldTransition, newTransition) in transitionsReplacement) {
                        newTargetInTransitionsMap.addReplacement(
                            oldTransition.target,
                            oldTransition,
                            listOfNotNull(newTransition),
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

    private fun State.getReplacement(inTransition: Transition): Map<Transition, Transition?> {
        val newSource = inTransition.source
        val replacement = LinkedHashMap<Transition, Transition?>()
        for (oldOutTransition in outTransitions) {
            val isEnclosedEpsilonTransition =
                oldOutTransition is EpsilonTransition && newSource === oldOutTransition.target
            val isNewTransitionAlreadyPresented by lazy(LazyThreadSafetyMode.NONE) {
                newSource.outTransitions.any {
                    it.checkExistingByInfo(oldOutTransition) || it.target === oldOutTransition.target
                }
            }

            replacement[oldOutTransition] = if (isEnclosedEpsilonTransition || isNewTransitionAlreadyPresented) {
                null // Old transitions should be stored anyway for removing later
            } else {
                val newTreeNodes = if (oldOutTransition is EndTransition) {
                    if (newSource is RootState) {
                        // Root states don't have incoming transitions -> trying to extract tree nodes from the in-transition
                        inTransition.treeNodes
                    } else {
                        // Extract nodes from the previous source's in-transitions and skip removing epsilons
                        newSource.inTransitions.filter { it !is EpsilonTransition }.flatMap { it.treeNodes }
                            .distinct()
                    }
                } else {
                    oldOutTransition.treeNodes
                }
                oldOutTransition.clone(newSource, oldOutTransition.target, newTreeNodes)
            }
        }
        return replacement
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
            else -> error("Unknown transition type: ${this@AtnEpsilonRemover}")
        }

        return treeNodes === other.treeNodes
    }
}