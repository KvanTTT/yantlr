package atn

import EmptyClosure
import EmptyToken
import SemanticsDiagnostic
import parser.AntlrNode

class AtnEpsilonRemover(val diagnosticReporter: ((SemanticsDiagnostic) -> Unit)? = null) {
    fun run(atn: Atn) {
        // Prevent duplicate diagnostics on the same nodes
        val emptyClosureAntlrNodes = mutableSetOf<AntlrNode>()

        fun <T : RootState> run(rootStates: List<T>) = rootStates.forEach { rootState ->
            do {
                // Repeat removing until no epsilon transitions left (fixed-point theorem)
                if (!run(rootState, emptyClosureAntlrNodes)) break
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

    private fun run(rootState: RootState, emptyClosureAntlrNodes: MutableSet<AntlrNode>): Boolean {
        val visitedStates = mutableSetOf<State>()
        var containsEpsilonTransition = false

        fun runInternal(currentState: State) {
            if (!visitedStates.add(currentState)) return

            containsEpsilonTransition = removeNextEpsilon(currentState, emptyClosureAntlrNodes) or containsEpsilonTransition

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
    private fun removeNextEpsilon(state: State, emptyClosureAntlrNodes: MutableSet<AntlrNode>): Boolean {
        val epsilonTransitionIndex =
            state.outTransitions.indexOfFirst { it.data is EpsilonTransitionData }.takeIf { it != -1 } ?: return false
        val epsilonTransition = state.outTransitions[epsilonTransitionIndex]
        epsilonTransition.unbind() // Remove current epsilon

        if (epsilonTransition.isEnclosed) {
            // Optimization: looped transitions are disallowed, and they are just being removed,
            // because they don't affect resulting ATN
            diagnosticReporter?.invoke(EmptyClosure((epsilonTransition.data as EpsilonTransitionData).antlrNode))
            return false
        }

        // Pair of transition data, and it's target state (source state is just `state`)
        val newOutTransitionsData = mutableListOf<Pair<TransitionData, State>>()

        val target = epsilonTransition.target
        for (targetOutTransition in target.outTransitions) {
            val data = targetOutTransition.data
            // Ignore binding of enclosed epsilon transitions
            if (data is EpsilonTransitionData && targetOutTransition.target === state) {
                reportEmptyClosureIfNeeded(epsilonTransition.data as EpsilonTransitionData, data, emptyClosureAntlrNodes)
                continue
            }
            // Ignore binding of transitions with existing data (on closures)
            if (state.outTransitions.any { it.data === data }) {
                continue
            }

            newOutTransitionsData.add(targetOutTransition.data to targetOutTransition.target)
        }

        // Insert at removing index to preserve transitions order
        state.outTransitions.addAll(epsilonTransitionIndex, newOutTransitionsData.map { (data, target) ->
            Transition(data, state, target).also { target.inTransitions.add(it) }
        })

        // If the target state of the epsilon transition becomes an orphan, it should be just removed
        epsilonTransition.target.unbindOutsIfNoIns()

        return true
    }

    /**
     * YANTLR can handle `EPSILON_CLOSURE` unlike ANTLR
     * However, the order of transitions becomes unobvious, that's why it's better to disallow such a closure
     */
    private fun reportEmptyClosureIfNeeded(
        removingEpsilonTransitionData: EpsilonTransitionData,
        currentEpsilonTransitionData: EpsilonTransitionData,
        emptyClosureAntlrNodes: MutableSet<AntlrNode>
    ) {
        val removingTransitionAntlrNode = removingEpsilonTransitionData.antlrNode
        val currentTransitionAntlrNode = currentEpsilonTransitionData.antlrNode
        val removingTransitionAntlrNodeLength = removingTransitionAntlrNode.getInterval().length
        val currentTransitionAntlrNodeLength = currentTransitionAntlrNode.getInterval().length

        // Check equality to zero to make sure it's an empty element
        // And check antlr nodes equality to make sure it's a real epsilon (empty) closure
        val emptyClosureNode = when {
            removingTransitionAntlrNodeLength == 0 -> {
                if (currentTransitionAntlrNodeLength == 0) emptyClosureAntlrNodes.add(currentTransitionAntlrNode)
                removingTransitionAntlrNode
            }
            currentTransitionAntlrNodeLength == 0 -> currentTransitionAntlrNode
            removingTransitionAntlrNode !== currentTransitionAntlrNode -> {
                // Choose the shortest one since it provides more information about error
                if (removingTransitionAntlrNodeLength < currentTransitionAntlrNodeLength)
                    removingTransitionAntlrNode
                else
                    currentTransitionAntlrNode
            }
            else -> null
        }
        emptyClosureNode?.let {
            // Check for duplicating to prevent diagnostic duplication
            if (emptyClosureAntlrNodes.add(it)) {
                diagnosticReporter?.invoke(EmptyClosure(it))
            }
        }
    }
}