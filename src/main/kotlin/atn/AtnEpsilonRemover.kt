package atn

import EmptyToken
import SemanticsDiagnostic

class AtnEpsilonRemover(val diagnosticReporter: ((SemanticsDiagnostic) -> Unit)? = null) {
    fun run(atn: Atn) {
        fun <T : RootState> run(rootStates: List<T>) = rootStates.forEach { rootState ->
            do {
                // Repeat removing until no epsilon transitions left (fixed-point theorem)
                if (!run(rootState)) break
            } while (true)

            for (outTransition in rootState.outTransitions) {
                if (outTransition.data is EndTransitionData) {
                    val rule = outTransition.data.rule
                    if (rule.isLexer && !rule.isFragment) {
                        diagnosticReporter?.invoke(EmptyToken(rule))
                    }
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

            containsEpsilonTransition = removeNextEpsilon(currentState) or containsEpsilonTransition

            // Allocated a list to prevent concurrent modification exception
            currentState.outTransitions.map { it.target }.forEach { runInternal(it) }
        }

        runInternal(rootState)
        return containsEpsilonTransition
    }

    /**
     * Returns true if epsilon transition found and removed but
     * returns false if that transition is looped (it doesn't affect anything)
     */
    private fun removeNextEpsilon(state: State): Boolean {
        val epsilonTransitionIndex =
            state.outTransitions.indexOfFirst { it.data is EpsilonTransitionData }.takeIf { it != -1 } ?: return false
        val epsilonTransition = state.outTransitions[epsilonTransitionIndex]
        epsilonTransition.unbind()

        if (epsilonTransition.isEnclosed) {
            // Optimization: looped transitions just removed, because they don't affect resulting ATN
            return false
        }

        // Pair of transition data, and it's target state (source state is just `state`)
        val newOutTransitionsData = mutableListOf<Pair<TransitionData, State>>()

        val target = epsilonTransition.target
        for (targetOutTransition in target.outTransitions) {
            val data = targetOutTransition.data
            // Ignore binding of enclosed epsilon transitions and transitions with existing data (on closures)
            if (data is EpsilonTransitionData && targetOutTransition.target === state ||
                state.outTransitions.any { it.data === data }
            ) {
                continue
            }

            newOutTransitionsData.add(targetOutTransition.data to targetOutTransition.target)
        }

        // Transition order should be preserved
        state.outTransitions.addAll(epsilonTransitionIndex, newOutTransitionsData.map { (data, target) ->
            Transition(data, state, target).also { target.inTransitions.add(it) }
        })

        // If the target state of the epsilon transition becomes an orphan, it should be just removed
        if (epsilonTransition.target.inTransitions.all { it.isEnclosed }) {
            epsilonTransition.target.unbindOuts()
        }

        return true
    }
}