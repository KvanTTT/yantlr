package atn

import SemanticsDiagnostic
import semantics.Rule
import java.util.*

class AtnDisambiguator(
    val diagnosticReporter: ((SemanticsDiagnostic) -> Unit)? = null,
    private val currentStateNumber: Int? = null
) {
    private var stateCounter: Int = currentStateNumber ?: 0

    fun run(atn: Atn) {
        stateCounter = currentStateNumber ?: atn.stateCounter

        fun <T : RootState> run(rootStates: List<T>) {
            rootStates.forEach {
                do {
                    // Repeat removing until no ambiguity transitions left (fixed-point theorem)
                    if (!run(it)) break
                } while (true)
            }
        }

        run(atn.modeStartStates)
        run(atn.lexerStartStates)
        run(atn.parserStartStates)
    }

    private fun run(rootState: State): Boolean {
        val visitedStates: MutableSet<State> = mutableSetOf()
        var containsAmbiguity = false

        fun runInternal(currentState: State) {
            if (!visitedStates.add(currentState)) return

            val oldTargets = currentState.outTransitions.map { it.target }

            containsAmbiguity = performDisambiguation(currentState) or containsAmbiguity

            // Run recursively for all out transitions and create a map to avoid concurrent modification
            oldTargets.forEach { runInternal(it) }
        }

        runInternal(rootState)
        return containsAmbiguity
    }

    internal fun performDisambiguation(currentState: State): Boolean {
        // Optimization: no need to process if there is zero or one transition
        if (currentState.outTransitions.size <= 1) return false

        val disjointGroups = buildDisjointGroups(currentState.outTransitions)

        // Optimization: there is nothing to merge since there are no new states or transitions
        if (disjointGroups.all { it.type == DisjointInfoType.NoChange }) return false

        // Create new states and helper map
        val oldToNewStatesMap: MutableMap<State, MutableSet<State>> = mutableMapOf()
        val oldOutTransitionsToRemap: MutableMap<State, List<Transition<*>>> = mutableMapOf()

        for (disjointGroup in disjointGroups) {
            if (disjointGroup.type == DisjointInfoType.NewState) {
                // Preserve old out transitions for later use
                val newState = disjointGroup.state
                val oldOutTransition = oldOutTransitionsToRemap[newState]
                if (oldOutTransition == null) {
                    oldOutTransitionsToRemap[newState] = buildList {
                        for (oldState in disjointGroup.oldStates) {
                            addAll(oldState.outTransitions)
                            oldToNewStatesMap.getOrPut(oldState) { mutableSetOf() }.add(newState)
                        }
                    }
                }
            }
        }

        // Unbind all out transitions (needed info is kept by `oldToNewStatesMap` and `oldOutTransitionsToRemap`)
        currentState.unbindOuts()

        // Bind new disambiguated transitions
        for (disjointGroup in disjointGroups) {
            disjointGroup.data.bind(currentState, disjointGroup.state)
        }

        // Remap out transitions to newly created states
        for ((newState, oldOutTransitions) in oldOutTransitionsToRemap) {
            for (oldOutTransition in oldOutTransitions) {
                val newTargetStates = oldToNewStatesMap[oldOutTransition.target]

                fun Transition<*>.bindIfNeeded(target: State) {
                    // Ignore the transition if not enclosed transition becomes enclosed
                    if (isEnclosed || newState !== target) {
                        data.bind(newState, target)
                    }
                }

                if (newTargetStates != null) {
                    for (newTargetState in newTargetStates) {
                        oldOutTransition.bindIfNeeded(newTargetState)
                    }
                } else {
                    oldOutTransition.bindIfNeeded(oldOutTransition.target)
                }
            }
        }

        // If the old target state becomes orphan, it should be unbound
        for (oldOutTransitions in oldOutTransitionsToRemap.values) {
            for (oldOutTransition in oldOutTransitions) {
                val source = oldOutTransition.source
                if (source.inTransitions.all { it.isEnclosed }) {
                    source.unbindOuts()
                }
            }
        }

        // Only new state may affect result ATN, so there is no need to recheck if no new states were created
        return disjointGroups.any { it.type == DisjointInfoType.NewState }
    }

    internal enum class DisjointInfoType {
        NoChange,
        NewTransition,
        NewState,
    }

    internal inner class DisjointTransitionInfo<T : TransitionData>(
        val type: DisjointInfoType,
        val data: T,
        val state: State,
        val oldStates: Set<State>
    ) {
        override fun toString(): String {
            return "(${oldStates.joinToString(", ") { it.number.toString() }}; ${type})"
        }
    }

    private data class IntervalInfo(val setTransition: Transition<IntervalTransitionData>, val start: Boolean)

    internal fun buildDisjointGroups(outTransitions: List<Transition<*>>): List<DisjointTransitionInfo<*>> {
        val intervalInfoMap = sortedMapOf<Int, MutableList<IntervalInfo>>()

        val ruleToTransitionMap: MutableMap<Rule, MutableList<Transition<RuleTransitionData>>> = mutableMapOf()
        val endRuleTransitionMap: MutableMap<Rule, MutableList<Transition<EndTransitionData>>> = mutableMapOf()
        val errorTransitions: MutableList<Transition<ErrorTransitionData>> = mutableListOf()
        val transitionOrderMap: MutableMap<Transition<*>, Int> = mutableMapOf()

        // Collect interval infos for every set transition and collect other transitions with their order
        @Suppress("UNCHECKED_CAST")
        for ((index, transition) in outTransitions.withIndex()) {
            transitionOrderMap[transition] = index
            when (val data = transition.data) {
                is EpsilonTransitionData -> {
                    error("Epsilon transitions should be removed before AtnDisambiguator running")
                }

                is SetTransitionData -> {
                    error("Should not be used in AtnDisambiguator (use `IntervalTransitionData` instead)")
                }

                is IntervalTransitionData -> {
                    transition as Transition<IntervalTransitionData>
                    intervalInfoMap.getOrPut(data.interval.start) { mutableListOf() }
                        .add(IntervalInfo(transition, start = true))
                    intervalInfoMap.getOrPut(data.interval.end + 1) { mutableListOf() }
                        .add(IntervalInfo(transition, start = false))
                }

                is RuleTransitionData -> {
                    ruleToTransitionMap.getOrPut(data.rule) { mutableListOf() }
                        .add(transition as Transition<RuleTransitionData>)
                }

                is EndTransitionData -> {
                    endRuleTransitionMap.getOrPut(data.rule) { mutableListOf() }
                        .add(transition as Transition<EndTransitionData>)
                }

                is ErrorTransitionData -> {
                    errorTransitions.add(transition as Transition<ErrorTransitionData>)
                }
            }
        }

        // Move over sorted intervals and assign them to the corresponding transitions (search intersections)
        val intervalsToTransitions: Map<Interval, List<Transition<IntervalTransitionData>>> =
            intervalInfoMap.collectIntervalDataToTransitions()

        // Build the final list:
        //  - Create new states if needed
        //  - Calculate type of newly created info
        //  - Try to preserve order of transitions if possible

        val disjointInfos: MutableMap<Int, MutableList<DisjointTransitionInfo<*>>> = mutableMapOf()

        // Reuse the same new state in case of different transitions to the same set of states
        val statesMap: MutableMap<Set<State>, State> = mutableMapOf()

        intervalsToTransitions.forEach { (interval, intervalTransitions) ->
            disjointInfos.add(interval, intervalTransitions, transitionOrderMap, statesMap)
        }

        ruleToTransitionMap.forEach { (rule, ruleTransitions) ->
            disjointInfos.add(rule, ruleTransitions, transitionOrderMap, statesMap)
        }

        endRuleTransitionMap.forEach { (rule, endRuleTransitions) ->
            disjointInfos.add(rule, endRuleTransitions, transitionOrderMap, statesMap)
        }

        if (errorTransitions.isNotEmpty()) {
            disjointInfos.add(null, errorTransitions, transitionOrderMap, statesMap)
        }

        return disjointInfos.toSortedMap().flatMap { it.value }
    }

    private fun SortedMap<Int, MutableList<IntervalInfo>>.collectIntervalDataToTransitions()
            : Map<Interval, List<Transition<IntervalTransitionData>>> {
        val disjointIntervals = mutableMapOf<Interval, List<Transition<IntervalTransitionData>>>()
        val currentSetTransitions = mutableListOf<Transition<IntervalTransitionData>>()
        var previousValue: Int? = null

        for ((value, intervalInfos) in this) {
            if (previousValue != null && currentSetTransitions.isNotEmpty()) {
                disjointIntervals[Interval(previousValue, value - 1)] = currentSetTransitions.toList()
            }

            for (intervalInfo in intervalInfos) {
                if (intervalInfo.start) {
                    currentSetTransitions.add(intervalInfo.setTransition)
                } else {
                    require(currentSetTransitions.remove(intervalInfo.setTransition))
                }
            }

            previousValue = value
        }

        return disjointIntervals
    }

    private fun MutableMap<Int, MutableList<DisjointTransitionInfo<*>>>.add(
        data: Any?,
        groupedTransitions: List<Transition<*>>,
        transitionOrderMap: MutableMap<Transition<*>, Int>,
        statesMap: MutableMap<Set<State>, State>
    ) {
        val oldStates = groupedTransitions.map { it.target }.toSet()
        val antlrNodes = groupedTransitions.flatMap { it.data.antlrNodes }.distinct()

        fun <T : TransitionData> getType(existingData: T, checkData: (T) -> Boolean): DisjointInfoType {
            return when {
                oldStates.size > 1 -> {
                    DisjointInfoType.NewState
                }
                !checkData(existingData) || antlrNodes != existingData.antlrNodes -> {
                    DisjointInfoType.NewTransition
                }
                else -> {
                    DisjointInfoType.NoChange
                }
            }
        }

        val type: DisjointInfoType
        val firstTransition = groupedTransitions.first()
        val newData = when (val firstTransitionData = firstTransition.data) {
            is IntervalTransitionData -> {
                val interval = data as Interval
                type = getType(firstTransitionData) { interval == it.interval }
                IntervalTransitionData(interval, antlrNodes)
            }

            is RuleTransitionData -> {
                val rule = data as Rule
                type = getType(firstTransitionData) { rule == it.rule }
                RuleTransitionData(data, antlrNodes)
            }

            is EndTransitionData -> {
                val rule = data as Rule
                type = getType(firstTransitionData) { rule == it.rule }
                EndTransitionData(data, antlrNodes)
            }

            is ErrorTransitionData -> {
                // Don't group error transitions
                type = DisjointInfoType.NoChange
                firstTransition.data
            }

            else -> {
                error("Should not be here")
            }
        }

        val newState = if (type == DisjointInfoType.NewState) {
            statesMap.getOrPut(oldStates) { State(stateCounter++) }
        } else {
            firstTransition.target
        }

        val disjointTransitionInfo = DisjointTransitionInfo(type, newData, newState, oldStates)
        // Try to preserve the order of transitions
        val order = groupedTransitions.minOf { transitionOrderMap.getValue(it) }
        getOrPut(order) { mutableListOf() }.add(disjointTransitionInfo)
    }
}