package atn

import EmptyToken
import SemanticsDiagnostic

class AtnEpsilonRemover(val diagnosticReporter: ((SemanticsDiagnostic) -> Unit)? = null) {
    fun run(atn: Atn) {
        fun <T : RootState> run(rootStates: List<T>) = rootStates.forEach { rootState ->
            do {
                if (!run(rootState)) break
            } while (true)

            for (outTransition in rootState.outTransitions) {
                if (outTransition is EndTransition && outTransition.rule.let { it.isLexer && !it.isFragment }) {
                    diagnosticReporter?.invoke(EmptyToken(outTransition.rule))
                }
            }
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
                    it.checkByInfo(oldOutTransition) && it.target === oldOutTransition.target
                }
            }

            replacement[oldOutTransition] = if (isEnclosedEpsilonTransition || isNewTransitionAlreadyPresented) {
                null // Old transitions should be stored anyway for removing later
            } else {
                // No need to perform deep clone because `AtnEpsilonRemover` doesn't change transition info
                oldOutTransition.clone(newSource, oldOutTransition.target, deep = false)
            }
        }
        return replacement
    }
}