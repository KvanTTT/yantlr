package atn

internal class TransitionReplacementMap {
    private val inTransitionsMap: MutableMap<State, MutableMap<Transition, MutableList<Transition>>> = mutableMapOf()
    private val outTransitionsMap: MutableMap<State, MutableMap<Transition, MutableList<Transition>>> = mutableMapOf()

    fun checkOutTransitions(state: State, condition: (Transition) -> Boolean): Boolean {
        val map = outTransitionsMap[state] ?: return state.outTransitions.any { condition(it) }

        for (outTransition in state.outTransitions) {
            val replacement = map[outTransition]
            if (replacement != null) {
                if (replacement.any { condition(it) }) {
                    return true
                }
            } else if (condition(outTransition)) {
                return true
            }
        }

        return false
    }

    fun addInForRemoving(state: State, oldTransition: Transition) =
        addReplacement(state, oldTransition, emptyList(), isOutTransition = false, preserveOldTransition = false)

    fun addOutForRemoving(state: State, oldTransition: Transition) =
        addReplacement(state, oldTransition, emptyList(), isOutTransition = true, preserveOldTransition = false)

    fun addInReplacement(
        state: State,
        oldTransition: Transition,
        addingTransitions: Collection<Transition>,
        preserveOldTransition: Boolean,
    ) = addReplacement(state, oldTransition, addingTransitions, isOutTransition = false, preserveOldTransition)

    fun addOutReplacement(
        state: State,
        oldTransition: Transition,
        addingTransitions: Collection<Transition>,
        preserveOldTransition: Boolean,
    ) = addReplacement(state, oldTransition, addingTransitions, isOutTransition = true, preserveOldTransition)

    private fun addReplacement(
        state: State,
        oldTransition: Transition,
        addingTransitions: Collection<Transition>,
        isOutTransition: Boolean,
        preserveOldTransition: Boolean,
    ) {
        val map = if (isOutTransition) outTransitionsMap else inTransitionsMap
        val newTransitions = map.getOrPut(state) { mutableMapOf() }.getOrPut(oldTransition) {
            mutableListOf<Transition>().also { if (preserveOldTransition) it.add(oldTransition) }
        }
        newTransitions.addAll(addingTransitions)
    }

    fun rebuildTransitions() {
        rebuildTransitions(isOutTransitions = false)
        rebuildTransitions(isOutTransitions = true)
    }

    private fun rebuildTransitions(isOutTransitions: Boolean) {
        val transitions = if (isOutTransitions) outTransitionsMap else inTransitionsMap
        for ((state, transitionReplacement) in transitions) {
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
}