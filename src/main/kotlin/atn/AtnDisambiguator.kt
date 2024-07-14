package atn

import SemanticsDiagnostic
import parser.ElementNode
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
                // All negation transitions should be removed on the first step
                searchNegationStates(it)

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

    private val negationStateMap: MutableMap<ElementNode, State> = mutableMapOf()

    private fun searchNegationStates(state: State) {
        negationStateMap.clear()
        val visitedStates: MutableSet<State> = mutableSetOf()
        val currentNegationNodes: MutableList<ElementNode> = mutableListOf()

        fun searchNegationStatesInternal(state: State) {
            if (!visitedStates.add(state)) return

            for (outTransition in state.outTransitions) {
                (outTransition.data as? NegationTransitionData)?.negationNode?.let { currentNegationNodes.add(it) }

                val antlrNodeEndOffset by lazy(LazyThreadSafetyMode.NONE) {
                    outTransition.data.antlrNodes.minOf { it.getInterval().end() }
                }
                currentNegationNodes.removeAll {
                    // Check by end offset to handle EndTransition correctly
                    if (antlrNodeEndOffset > it.getInterval().end()) {
                        negationStateMap.putIfAbsent(it, state)
                        true
                    } else {
                        false
                    }
                }
            }

            state.outTransitions.forEach { searchNegationStatesInternal(it.target) }
        }

        searchNegationStatesInternal(state)
    }

    private fun run(rootState: State): Boolean {
        val visitedStates: MutableSet<State> = mutableSetOf()
        var mayContainAmbiguity = false

        fun runInternal(currentState: State) {
            if (!visitedStates.add(currentState)) return

            val oldTargets = currentState.outTransitions.map { it.target }

            mayContainAmbiguity = performDisambiguation(currentState) or mayContainAmbiguity

            // Run recursively, but ignore new states to avoid infinite loop (fixed-point)
            // The new states will be processed in the next iteration
            oldTargets.forEach { runInternal(it) }
        }

        runInternal(rootState)
        return mayContainAmbiguity
    }

    internal fun performDisambiguation(currentState: State): Boolean {
        // Optimization: no need to process if there is zero or one transition
        val outTransitions = currentState.outTransitions
        if (outTransitions.isEmpty()) return false
        if (outTransitions.size == 1) {
            if (outTransitions.single().data.let { (it as? NegationTransitionData)?.negationNode } == null) return false
        }

        val disjointGroups = buildDisjointGroups(outTransitions)

        // Optimization: there is nothing to merge since there are no new states or transitions
        if (disjointGroups.isNotEmpty() && // A special case: out states become unreachable (TODO: report a diagnostic?)
            disjointGroups.all { it.type == DisjointInfoType.NoChange }
        ) {
            return false
        }

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
                oldOutTransition.source.unbindOutsIfNoIns()
            }
        }
        for (disjointGroup in disjointGroups) {
            if (disjointGroup.type == DisjointInfoType.NegationTransition) {
                disjointGroup.oldStates.forEach(State::unbindOutsIfNoIns)
            }
        }

        // Only new state may affect result ATN, so there is no need to recheck if no new states were created
        return disjointGroups.any { it.type >= DisjointInfoType.NegationTransition }
    }

    internal enum class DisjointInfoType {
        NoChange,
        MergedTransition,
        NegationTransition,
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
        val intervalInfoMap: SortedMap<Int, MutableList<IntervalInfo>> = sortedMapOf()
        val negationIntervalInfoMap: MutableMap<ElementNode, SortedMap<Int, MutableList<IntervalInfo>>> = mutableMapOf()

        val ruleToTransitionMap: MutableMap<Rule, MutableList<Transition<RuleTransitionData>>> = mutableMapOf()
        val endRuleTransitionMap: MutableMap<Rule, MutableList<Transition<EndTransitionData>>> = mutableMapOf()

        val transitionOrderMap: MutableMap<Transition<*>, Int> = mutableMapOf()

        // Collect interval infos for every set transition and collect other transitions with their order
        @Suppress("UNCHECKED_CAST")
        for ((index, transition) in outTransitions.withIndex()) {
            transitionOrderMap[transition] = index
            when (val data = transition.data) {
                is EpsilonTransitionData -> {
                    error("Epsilon transitions should be removed before AtnDisambiguator running")
                }

                is IntervalTransitionData -> {
                    transition as Transition<IntervalTransitionData>
                    val interval = data.interval

                    fun SortedMap<Int, MutableList<IntervalInfo>>.addIntervalInfo(transition: Transition<IntervalTransitionData>) {
                        getOrPut(interval.start) { mutableListOf() }
                            .add(IntervalInfo(transition, start = true))
                        getOrPut(interval.end + 1) { mutableListOf() }
                            .add(IntervalInfo(transition, start = false))
                    }

                    val negationNode= data.negationNode
                    if (negationNode == null) {
                        intervalInfoMap.addIntervalInfo(transition)
                    } else {
                        val negationNodeEnd = negationNode.getInterval().end()
                        val dropRegularTransition = transition.target.outTransitions.any {
                            it.data.antlrNodes.any { antlrNode ->
                                antlrNode.getInterval().end() > negationNodeEnd
                            }
                        }
                        // If the current transition is the last in negation sequence, it should be removed
                        if (!dropRegularTransition) {
                            Transition(
                                IntervalTransitionData(interval, data.antlrNodes, negationNode = null),
                                transition.source,
                                transition.target,
                            ).also {
                                transitionOrderMap[it] = index
                                intervalInfoMap.addIntervalInfo(it)
                            }
                        }

                        val negateMap = negationIntervalInfoMap.getOrPut(data.negationNode) { sortedMapOf() }

                        val negateTransition = Transition(
                            IntervalTransitionData(interval, data.antlrNodes, negationNode = negationNode),
                            transition.source,
                            transition.target,
                        )

                        negateMap.addIntervalInfo(negateTransition)
                    }
                }

                is RuleTransitionData -> {
                    ruleToTransitionMap.getOrPut(data.rule) { mutableListOf() }
                        .add(transition as Transition<RuleTransitionData>)
                }

                is EndTransitionData -> {
                    endRuleTransitionMap.getOrPut(data.rule) { mutableListOf() }
                        .add(transition as Transition<EndTransitionData>)
                }
            }
        }

        // Move over sorted intervals and assign them to the corresponding transitions (search intersections)
        val intervalsToTransitions: Map<Interval, List<Transition<IntervalTransitionData>>> =
            intervalInfoMap.collectIntervalDataToTransitions(negation = false)

        val negationIntervalsToTransitionsList: List<Map<Interval, List<Transition<IntervalTransitionData>>>> =
            negationIntervalInfoMap.values.map { it.collectIntervalDataToTransitions(negation = true) }

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

        negationIntervalsToTransitionsList.forEach { negationIntervalsToTransitions ->
            negationIntervalsToTransitions.forEach { (interval, intervalTransitions) ->
                disjointInfos.add(interval, intervalTransitions, transitionOrderMap, statesMap)
            }
        }

        ruleToTransitionMap.forEach { (rule, ruleTransitions) ->
            disjointInfos.add(rule, ruleTransitions, transitionOrderMap, statesMap)
        }

        endRuleTransitionMap.forEach { (rule, endRuleTransitions) ->
            disjointInfos.add(rule, endRuleTransitions, transitionOrderMap, statesMap)
        }

        return disjointInfos.toSortedMap().flatMap { it.value }
    }

    private fun SortedMap<Int, MutableList<IntervalInfo>>.collectIntervalDataToTransitions(negation: Boolean)
            : Map<Interval, List<Transition<IntervalTransitionData>>> {
        val disjointIntervals = mutableMapOf<Interval, List<Transition<IntervalTransitionData>>>()

        val initialSetTransitions: List<Transition<IntervalTransitionData>>
        var previousValue: Int?

        if (!negation) {
            initialSetTransitions = listOf()
            previousValue = null
        } else {
            initialSetTransitions = values.flatten().filter { it.start }.map { it.setTransition }
            previousValue = Interval.MIN
        }

        val currentSetTransitions: MutableList<Transition<IntervalTransitionData>> =
            initialSetTransitions.toMutableList()

        for ((value, intervalInfos) in this) {
            if (previousValue != null && value > previousValue) { // Check for a bound case (MIN)
                if (!negation) {
                    if (currentSetTransitions.isNotEmpty()) {
                        disjointIntervals[Interval(previousValue, value - 1)] = currentSetTransitions.toList()
                    }
                } else {
                    if (currentSetTransitions.size == initialSetTransitions.size) {
                        disjointIntervals[Interval(previousValue, value - 1)] = initialSetTransitions
                    }
                }
            }

            for (intervalInfo in intervalInfos) {
                val shouldAdd = intervalInfo.start xor negation

                if (shouldAdd) {
                    currentSetTransitions.add(intervalInfo.setTransition)
                } else {
                    val isRemoved = currentSetTransitions.remove(intervalInfo.setTransition)
                    require(isRemoved)
                }
            }

            previousValue = value
        }

        if (negation && // Process the remaining interval for negation
            previousValue != null &&
            previousValue <= Interval.MAX && // Check for a bound case (MAX)
            currentSetTransitions.size == initialSetTransitions.size
        ) {
            disjointIntervals[Interval(previousValue, Interval.MAX)] = initialSetTransitions
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

        val firstTransition = groupedTransitions.first()
        val newData = when (firstTransition.data) {
            is IntervalTransitionData -> IntervalTransitionData(data as Interval, antlrNodes, null)

            is RuleTransitionData -> RuleTransitionData(data as Rule, antlrNodes, null)

            is EndTransitionData -> EndTransitionData(data as Rule, antlrNodes)

            else -> error("Should not be here")
        }

        val type = when {
            groupedTransitions.any { it.data is NegationTransitionData && it.data.negationNode != null } ->
                DisjointInfoType.NegationTransition

            oldStates.size > 1 -> DisjointInfoType.NewState

            // Transitions with the same data should be merged
            groupedTransitions.size > 1 -> DisjointInfoType.MergedTransition

            else -> DisjointInfoType.NoChange
        }

        val state = when (type) {
            DisjointInfoType.NegationTransition -> {
                negationStateMap.getValue((firstTransition.data as NegationTransitionData).negationNode!!)
            }
            DisjointInfoType.NewState -> {
                statesMap.getOrPut(oldStates) { State(stateCounter++) }
            }
            else -> {
                firstTransition.target
            }
        }

        // Try to preserve the order of transitions
        val order = groupedTransitions.minOf { transitionOrderMap[it] ?: Int.MAX_VALUE }
        getOrPut(order) { mutableListOf() }.add(DisjointTransitionInfo(type, newData, state, oldStates))
    }
}