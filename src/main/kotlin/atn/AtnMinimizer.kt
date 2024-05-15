package atn

private typealias TransitionReplacementMap = MutableMap<State, MutableMap<Transition, MutableList<Transition>>>

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

            if (inEpsilonTransitions.isNotEmpty()) {
                // If all incoming transitions are epsilon, then the current state is effectively unreachable.
                // Thus, it can be removed
                val preserveCurrentState = inEpsilonTransitions.size != inTransitions.size

                val newSourceOutTransitionsMap: TransitionReplacementMap = mutableMapOf()
                val newTargetInTransitionsMap: TransitionReplacementMap = mutableMapOf()

                val outTransitions = currentState.outTransitions - inEpsilonTransitions // Filter out enclosed epsilon transitions
                for (inEpsilonTransition in inEpsilonTransitions) {
                    if (inEpsilonTransition.source == inEpsilonTransition.target) {
                        // Remove self-loop epsilon transition
                        newSourceOutTransitionsMap.addReplacement(
                            inEpsilonTransition.source,
                            inEpsilonTransition,
                            emptyList(),
                            preserveOldTransition = false
                        )
                        newTargetInTransitionsMap.addReplacement(
                            inEpsilonTransition.target,
                            inEpsilonTransition,
                            emptyList(),
                            preserveOldTransition = false
                        )
                    } else {
                        val newOutTransitions =
                            outTransitions.associateWith { it.clone(inEpsilonTransition.source, it.target) }

                        newSourceOutTransitionsMap.addReplacement(
                            inEpsilonTransition.source,
                            inEpsilonTransition,
                            newOutTransitions.values,
                            preserveOldTransition = false
                        )

                        for (outTransition in outTransitions) {
                            newTargetInTransitionsMap.addReplacement(
                                outTransition.target,
                                outTransition,
                                listOf(newOutTransitions.getValue(outTransition)),
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

    private fun TransitionReplacementMap.addReplacement(
        state: State,
        oldTransition: Transition,
        addingTransitions: Collection<Transition>,
        preserveOldTransition: Boolean
    ) {
        val newTransitions = getOrPut(state) { mutableMapOf() }.getOrPut(oldTransition) { mutableListOf() }
        if (preserveOldTransition) {
            newTransitions.add(oldTransition)
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
                        replacement.forEach { newTransition -> if (!checkExisting(newTransition)) add(newTransition)  }
                    } else if (!checkExisting(oldTransition)) {
                        add(oldTransition)
                    }
                }
            }
            oldTransitions.clear()
            oldTransitions.addAll(newTransitions)
        }
    }

    private fun List<Transition>.checkExisting(other: Transition): Boolean {
        return any { it.checkExisting(other) }
    }

    private fun Transition.checkExisting(other: Transition): Boolean {
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

        return source === other.source && target === other.target && treeNodes === other.treeNodes
    }
}