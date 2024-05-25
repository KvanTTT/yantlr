package atn

object AtnEpsilonRemover {
    fun run(atn: Atn) {
        fun <T : RootState> run(rootStates: List<T>) = rootStates.forEach { rootState ->
            do {
                if (!run(rootState)) break
            } while (true)
        }

        run(atn.modeStartStates)
        run(atn.lexerStartStates)
        run(atn.parserStartStates)
    }

    private fun run(rootState: RootState): Boolean {
        val visitedStates = mutableSetOf<State>()
        var containsEpsilonTransition = false

        fun runInternal(currentState: State) {
            if (!visitedStates.add(currentState)) return

            currentState.outTransitions.forEach { runInternal(it.target) }

            val epsilonTransitions = currentState.outTransitions.filterIsInstance<EpsilonTransition>()

            if (epsilonTransitions.isNotEmpty()) {
                val transitionsReplacement = TransitionReplacementMap()

                for (epsilonTransition in epsilonTransitions) {
                    val epsilonTarget = epsilonTransition.target

                    transitionsReplacement.addInForRemoving(epsilonTarget, epsilonTransition)

                    val replacementMap = epsilonTarget.getReplacement(epsilonTransition, transitionsReplacement)

                    transitionsReplacement.addOutReplacement(
                        currentState,
                        epsilonTransition,
                        replacementMap.values.filterNotNull(),
                        preserveOldTransition = false,
                    )

                    val preserveEpsilonTargetState =
                        epsilonTarget.inTransitions.any { !epsilonTransitions.contains(it) }

                    for ((oldTransition, newTransition) in replacementMap) {
                        transitionsReplacement.addInReplacement(
                            oldTransition.target,
                            oldTransition,
                            listOfNotNull(newTransition),
                            preserveOldTransition = preserveEpsilonTargetState,
                        )
                        if (!preserveEpsilonTargetState) {
                            transitionsReplacement.addOutForRemoving(epsilonTarget, oldTransition)
                        }
                    }
                }

                transitionsReplacement.rebuildTransitions()

                if (!containsEpsilonTransition && currentState.outTransitions.any { it is EpsilonTransition }) {
                    containsEpsilonTransition = true
                }
            }

            currentState.outTransitions.forEach { runInternal(it.target) }
        }

        runInternal(rootState)
        return containsEpsilonTransition
    }

    private fun State.getReplacement(oldEpsilon: Transition, transitionsReplacement: TransitionReplacementMap): Map<Transition, Transition?> {
        val newSource = oldEpsilon.source
        val replacement = LinkedHashMap<Transition, Transition?>()
        for (oldOutTransition in outTransitions) {
            val isEnclosedEpsilonTransition =
                oldOutTransition is EpsilonTransition && newSource === oldOutTransition.target
            val isNewTransitionAlreadyPresented by lazy(LazyThreadSafetyMode.NONE) {
                transitionsReplacement.checkOutTransitions(newSource) {
                    it.checkExistingByInfo(oldOutTransition) && it.target === oldOutTransition.target
                }
            }

            replacement[oldOutTransition] = if (isEnclosedEpsilonTransition || isNewTransitionAlreadyPresented) {
                null // Old transitions should be stored anyway for removing later
            } else {
                oldOutTransition.clone(newSource, oldOutTransition.target)
            }
        }
        return replacement
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