package atn

class AtnVerifier(val checkNoEpsilons: Boolean) {
    fun verify(atn: Atn) {
        fun verify(rootStates: List<RootState>) = rootStates.forEach { verify(it) }

        verify(atn.modeStartStates)
        verify(atn.lexerStartStates)
        verify(atn.parserStartStates)
    }

    private fun verify(rootState: RootState) {
        val visitedStates = mutableSetOf<State>()
        val inferredInTransitionsMap = mutableMapOf<State, MutableList<Transition>>()

        inferredInTransitionsMap[rootState] = mutableListOf()

        fun verifyInternal(currentState: State) {
            if (!visitedStates.add(currentState)) return

            var currentAntlrNodeOffset = 0
            for (outTransition in currentState.outTransitions) {
                // Checks for antlr nodes ordering
                currentAntlrNodeOffset = checkAntlrNodes(outTransition, currentAntlrNodeOffset)

                val target = outTransition.target

                if (outTransition.source != currentState) {
                    throw IllegalStateException("Source of out-transition does not match the current state: $outTransition")
                }

                if (checkNoEpsilons && outTransition is EpsilonTransition) {
                    throw IllegalStateException("Epsilon transition found in the ATN: $outTransition")
                }

                inferredInTransitionsMap.getOrPut(target) { mutableListOf() }.add(outTransition)

                verifyInternal(target)
            }
        }

        verifyInternal(rootState)

        for (state in visitedStates) {
            val inferredInTransitions = inferredInTransitionsMap.getValue(state).toSet()
            val actualInTransitions = state.inTransitions.toSet()

            val unexpectedInferredInTransitions = inferredInTransitions - actualInTransitions
            if (unexpectedInferredInTransitions.isNotEmpty()) {
                throw IllegalStateException("Unexpected inferred in-transitions $unexpectedInferredInTransitions for state $state")
            }

            val unexpectedActualInTransitions = actualInTransitions - inferredInTransitions
            if (unexpectedActualInTransitions.isNotEmpty()) {
                throw IllegalStateException("Unexpected actual in-transitions $unexpectedActualInTransitions for state $state")
            }
        }
    }

    private fun checkAntlrNodes(transition: Transition, previousMaxOffset: Int): Int {
        if (transition is EndTransition) return previousMaxOffset // End transitions are special

        val antlrNodes = transition.treeNodes

        if (antlrNodes.isEmpty()) {
            throw IllegalStateException("Out-transition $transition is not bound to any antlr node")
        }

        if (antlrNodes.toSet().size != antlrNodes.size) {
            throw IllegalStateException("Out-transition $transition has duplicate antlr nodes")
        }

        var currentAntlrNodeOffset = 0
        var maxOffset = 0

        for ((index, antlrNode) in antlrNodes.withIndex()) {
            val offset = antlrNode.getInterval().offset

            if (index == 0) {
                if (offset < previousMaxOffset) {
                    throw IllegalStateException("First antlr node in out-transition $transition has an offset different from the previous min offset: $antlrNode")
                }
            }

            if (index == antlrNodes.size - 1) {
                maxOffset = offset
            }

            if (offset < currentAntlrNodeOffset) {
                throw IllegalStateException("Found unsorted by offset antlr nodes in out-transition $transition")
            }
            currentAntlrNodeOffset = offset
        }

        return maxOffset
    }
}