package atn

import ElementsCollisionInSet
import SemanticsDiagnostic
import SourceInterval
import UnreachableElement
import parser.AntlrNode
import semantics.Rule
import java.util.*

class AtnDisambiguator(
    val diagnosticReporter: ((SemanticsDiagnostic) -> Unit)? = null,
    private val currentStateNumber: Int? = null
) {
    private var stateCounter: Int = currentStateNumber ?: 0

    // Reuse the same new state in case of different transitions to the same set of states
    private val statesMap: MutableMap<Set<State>, State> = mutableMapOf()
    private val newStatesMap: MutableMap<State, Set<State>> = mutableMapOf()
    private val unreachableAntlrElements: MutableSet<AntlrNode> = mutableSetOf()

    fun run(atn: Atn) {
        stateCounter = currentStateNumber ?: atn.stateCounter

        fun <T : RootState> run(rootStates: List<T>) {
            rootStates.forEach {
                statesMap.clear()
                newStatesMap.clear()
                unreachableAntlrElements.clear()
                do {
                    // Repeat removing until no ambiguity transitions left (fixed-point theorem)
                    if (!run(it)) break
                } while (true)

                removeUnreachableStates(it)
            }
        }

        run(atn.modeStartStates)
        run(atn.lexerStartStates)
        run(atn.parserStartStates)
    }

    private fun run(rootState: State): Boolean {
        val visitedStates: MutableSet<State> = mutableSetOf()

        var mayContainAmbiguity = false

        fun runInternal(currentState: State) {
            if (!visitedStates.add(currentState)) return

            mayContainAmbiguity = performDisambiguation(currentState) or mayContainAmbiguity

            currentState.outTransitions.map { it.target }.forEach { runInternal(it) }
        }

        runInternal(rootState)
        return mayContainAmbiguity
    }

    private fun removeUnreachableStates(rootState: State) {
        val reachableStates: MutableSet<State> = mutableSetOf()

        fun removeUnreachableStates(currentState: State) {
            if (!reachableStates.add(currentState)) return
            currentState.outTransitions.forEach { removeUnreachableStates(it.target) }
        }

        removeUnreachableStates(rootState)

        for (states in newStatesMap.values) {
            for (state in states) {
                if (state !in reachableStates) {
                    state.unbindOuts()
                }
            }
        }
    }

    internal fun performDisambiguation(state: State): Boolean {
        // Optimization: no need to process if there is zero or one transition
        if (state.outTransitions.size <= 1) return false

        val disjointGroups = buildDisjointGroups(state.outTransitions)

        // Optimization: there is nothing to merge since there are no new states or transitions
        if (disjointGroups.isNotEmpty() && // A special case: out states become unreachable (TODO: report a diagnostic?)
            disjointGroups.all { it.type == DisjointInfoType.NoChange }
        ) {
            return false
        }

        // Create new states and helper map
        val oldOutTransitionsToRemap: MutableMap<State, List<Transition<*>>> = mutableMapOf()

        var newStateCreated = false
        for (disjointGroup in disjointGroups) {
            if (disjointGroup.type == DisjointInfoType.NewState) {
                newStateCreated = true
                // Preserve old out transitions for later use
                oldOutTransitionsToRemap[disjointGroup.targetState] = buildList {
                    for (oldState in disjointGroup.oldStates) {
                        for (outTransition in oldState.outTransitions) {
                            if (!any { it.data === outTransition.data }) {
                                add(outTransition)
                            } else {
                                // TODO: it should report on more precise element,
                                //  but not at the end of unreachable antlr node
                                val unreachableAntlrNode =
                                    outTransition.source.inTransitions.flatMap { (it.data as RealTransitionData).antlrNodes }
                                        .distinct().single()
                                if (unreachableAntlrElements.add(unreachableAntlrNode)) {
                                    diagnosticReporter?.invoke(
                                        UnreachableElement(SourceInterval(unreachableAntlrNode.getInterval().end(), 0))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Unbind all out transitions (needed info is kept by `oldToNewStatesMap` and `oldOutTransitionsToRemap`)
        state.unbindOuts()

        // Bind new disambiguated transitions
        for (disjointGroup in disjointGroups) {
            disjointGroup.data.bind(state, disjointGroup.targetState)
        }

        // Remap out transitions to newly created states
        for ((newState, oldOutTransitions) in oldOutTransitionsToRemap) {
            for (oldOutTransition in oldOutTransitions) {
                oldOutTransition.data.bind(newState, oldOutTransition.target)
            }
        }

        // Only new state may affect result ATN, so there is no need to recheck if no new states were created
        return newStateCreated
    }

    internal enum class DisjointInfoType {
        NoChange,
        MergedTransition,
        NewState,
    }

    internal inner class DisjointTransitionInfo<T : TransitionData>(
        val type: DisjointInfoType,
        val data: T,
        val targetState: State,
        val oldStates: Set<State>
    ) {
        override fun toString(): String {
            return "(${oldStates.joinToString(", ") { it.number.toString() }}; ${type})"
        }
    }

    internal fun buildDisjointGroups(outTransitions: List<Transition<*>>): List<DisjointTransitionInfo<*>> {
        val intervalInfoMap: SortedMap<Int, MutableList<IntervalInfo>> = sortedMapOf()

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
                    require(data.negationNodes.isEmpty()) { "Not transitions should be processed at AtnNegationRemover" }
                    intervalInfoMap.addIntervalInfo(data.interval, transition)
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

        // Build the final list:
        //  - Create new states if needed
        //  - Calculate type of newly created info
        //  - Try to preserve order of transitions if possible

        val disjointInfos: MutableMap<Int, MutableList<DisjointTransitionInfo<*>>> = mutableMapOf()

        intervalsToTransitions.forEach { (interval, intervalTransitions) ->
            disjointInfos.add(interval, intervalTransitions, transitionOrderMap)
        }

        ruleToTransitionMap.forEach { (rule, ruleTransitions) ->
            disjointInfos.add(rule, ruleTransitions, transitionOrderMap)
        }

        endRuleTransitionMap.forEach { (rule, endRuleTransitions) ->
            disjointInfos.add(rule, endRuleTransitions, transitionOrderMap)
        }

        return disjointInfos.toSortedMap().flatMap { it.value }
    }

    private fun MutableMap<Int, MutableList<DisjointTransitionInfo<*>>>.add(
        groupKey: Any?,
        groupTransitions: List<Transition<*>>,
        transitionOrderMap: MutableMap<Transition<*>, Int>,
    ) {
        val oldStates = buildSet {
            groupTransitions.forEach {
                val newStates = newStatesMap[it.target]
                if (newStates != null) {
                    addAll(newStates)
                } else {
                    add(it.target)
                }
            }
        }

        val firstTransition = groupTransitions.first()

        val targetState: State
        val type: DisjointInfoType
        when {
            oldStates.size > 1 -> {
                val existingState = statesMap[oldStates]
                if (existingState == null) {
                    targetState = State(stateCounter++).also {
                        newStatesMap[it] = oldStates
                        statesMap[oldStates] = it
                    }
                    type = DisjointInfoType.NewState
                } else {
                    targetState = existingState
                    type = if (groupTransitions.size > 1) {
                        DisjointInfoType.MergedTransition
                    } else {
                        DisjointInfoType.NoChange
                    }
                }
            }

            // Transitions with the same data should be merged
            groupTransitions.size > 1 -> {
                targetState = firstTransition.target
                type = DisjointInfoType.MergedTransition
            }

            else -> {
                targetState = firstTransition.target
                type = DisjointInfoType.NoChange
            }
        }

        val newData = when (val data = firstTransition.data) {
            is RealTransitionData -> {
                val antlrNodes = groupTransitions.flatMap { (it.data as RealTransitionData).antlrNodes }.distinct()
                if (type == DisjointInfoType.MergedTransition && targetState !in newStatesMap) {
                    diagnosticReporter?.invoke(ElementsCollisionInSet(data, antlrNodes))
                }
                if (data is IntervalTransitionData) {
                    IntervalTransitionData(groupKey as Interval, antlrNodes)
                } else {
                    RuleTransitionData(groupKey as Rule, antlrNodes)
                }
            }

            is EndTransitionData -> EndTransitionData(groupKey as Rule)

            else -> error("Should not be here")
        }

        // Try to preserve the order of transitions
        val order = groupTransitions.minOf { transitionOrderMap.getValue(it) }
        getOrPut(order) { mutableListOf() }.add(DisjointTransitionInfo(type, newData, targetState, oldStates))
    }
}