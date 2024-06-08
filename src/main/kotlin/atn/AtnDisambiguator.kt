package atn

import Diagnostic
import SemanticsDiagnostic
import semantics.Rule
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.collections.LinkedHashSet
import kotlin.reflect.KClass

class AtnDisambiguator(val diagnosticReporter: ((SemanticsDiagnostic) -> Unit)? = null) {
    fun run(atn: Atn) {
        val helper = Helper(atn.stateCounter)
        fun <T : RootState> run(rootStates: List<T>) = rootStates.forEach {
            do {
                if (!helper.run(it)) break
            } while (true)
        }

        run(atn.modeStartStates)
        run(atn.lexerStartStates)
        run(atn.parserStartStates)
    }

    private class Helper(private var stateCounter: Int) {
        fun run(rootState: RootState): Boolean {
            val visitedStates = mutableSetOf<State>()
            var containsAmbiguity = false

            fun runInternal(currentState: State) {
                if (!visitedStates.add(currentState)) return

                containsAmbiguity = containsAmbiguity or performDisambiguation(currentState)

                // Run recursively for all out transitions and create map to avoid concurrent modification
                currentState.outTransitions.map { it.target }.forEach { runInternal(it) }
            }

            runInternal(rootState)

            return containsAmbiguity
        }

        private fun performDisambiguation(currentState: State): Boolean {
            // Optimization: no need to process if there is zero or one transition
            if (currentState.outTransitions.size <= 1) return false

            val disjointTransitionInfos = buildDisjointTransitions(currentState.outTransitions)
            // Optimization: there is nothing to merge since there are no new states
            if (disjointTransitionInfos.none { it.shouldRemap }) return false

            // Create new states, transitions and helper map
            val oldToNewStatesMap: MutableMap<State, LinkedHashSet<State>> = mutableMapOf()
            val refinedDisjointInfos: List<RefinedTransitionInfo> = buildList {
                for (info in disjointTransitionInfos) {
                    val treeNodes = info.transitions.singleOrNull()?.treeNodes
                        ?: info.transitions.flatMapTo(LinkedHashSet()) { it.treeNodes }
                    val stateNumber = if (info.keepStateNumber)
                        info.transitions.single().target.number
                    else
                        stateCounter++

                    val newState = State(stateNumber)

                    info.transitions.forEach {
                        oldToNewStatesMap.getOrPut(it.target) { LinkedHashSet() }.add(newState)
                    }

                    val newTransition = when (info.transitionClass) {
                        SetTransition::class -> {
                            SetTransition(info.data as IntervalSet, currentState, newState, treeNodes)
                        }
                        EndTransition::class -> {
                            EndTransition(info.data as Rule, currentState, newState, treeNodes)
                        }
                        ErrorTransition::class -> {
                            @Suppress("UNCHECKED_CAST")
                            ErrorTransition(info.data as LinkedHashSet<Diagnostic>, currentState, newState, treeNodes)
                        }
                        else -> {
                            error("Unsupported transition type: $info")
                        }
                    }

                    add(RefinedTransitionInfo(newTransition, info.transitions))
                }
            }

            // Unbind all out transitions (needed info is kept by `disjointTransitionInfos`)
            currentState.unbindOuts()

            // Bind new transitions and update related in and out transitions for new states
            for (refinedDisjointInfo in refinedDisjointInfos) {
                // Bind the new transition
                refinedDisjointInfo.newTransition.bind()

                val newState = refinedDisjointInfo.newState

                // Rebind out transitions to the new state
                refinedDisjointInfo.oldOutTransitions.forEach { transition ->
                    val newOutStates = oldToNewStatesMap[transition.target]
                    if (newOutStates == null) {
                        transition.bindOrMerge(newState, transition.target)
                    } else {
                        newOutStates.forEach { transition.bindOrMerge(newState, it) }
                    }
                }

                // Rebind old unrelated in transitions to the new state
                refinedDisjointInfo.oldExtraInTransitions.forEach { transition ->
                    val newInStates = oldToNewStatesMap[transition.source]
                    if (newInStates == null) {
                        val newTransition = transition.clone(transition.source, newState)
                        val outTransitions = transition.source.outTransitions

                        val newOutTransitions = LinkedHashSet<Transition>()
                        var isTransitionFound = false
                        for (outTransition in outTransitions) {
                            newOutTransitions.add(if (outTransition === transition) {
                                isTransitionFound = true
                                newTransition
                            } else {
                                outTransition
                            })
                        }
                        require(isTransitionFound)

                        outTransitions.clear()
                        outTransitions.addAll(newOutTransitions)
                        newState.inTransitions.add(newTransition)
                    } else {
                        for (newInState in newInStates) {
                            transition.clone(newInState, newState)
                        }
                    }
                }
            }

            // Unbind old transitions
            refinedDisjointInfos.forEach { info ->
                info.oldOutTransitions.forEach { it.unbind() }
                info.oldExtraInTransitions.forEach { it.unbind() }
            }

            // Remove possible orphan states
            for (oldState in oldToNewStatesMap.keys) {
                if (oldState.inTransitions.all { it.isLoop }) {
                    oldState.unbindOuts()
                }
            }

            return true
        }

        class RefinedTransitionInfo(val newTransition: Transition, outTransitions: List<Transition>) {
            val newState: State = newTransition.target
            val oldExtraInTransitions: LinkedHashSet<Transition> = LinkedHashSet(outTransitions.flatMap { it.target.inTransitions }
                .filter { it.source != newTransition.source })
            val oldOutTransitions: LinkedHashSet<Transition> = outTransitions.flatMapTo(LinkedHashSet()) { it.target.outTransitions }
        }

        class DisjointTransitionInfo<T: Transition, K: Any?>(val transitionClass: KClass<out Transition>, val data: K, val transitions: List<T>) {
            init { require(transitions.isNotEmpty()) }

            val shouldRemap: Boolean = run {
                if (transitions.size > 1) return@run true

                if (data is IntervalSet) {
                    return@run (transitions.single() as SetTransition).set != data
                }

                false
            }

            val keepStateNumber: Boolean = transitions.size == 1

            override fun toString(): String {
                return "(${transitions.size}, shouldRemap=$shouldRemap)"
            }
        }

        private data class IntervalInfo(val setTransition: SetTransition, val start: Boolean)

        private fun buildDisjointTransitions(transitions: LinkedHashSet<Transition>): List<DisjointTransitionInfo<*, *>> {
            val transitionOrder = mutableMapOf<Transition, Int>()
            val intervalInfoMap = sortedMapOf<Int, MutableList<IntervalInfo>>()

            // Collect interval infos for every set transition and collect other transitions with their order
            for ((index, transition) in transitions.withIndex()) {
                transitionOrder[transition] = index
                when (transition) {
                    is SetTransition -> {
                        for (interval in transition.set.intervals) {
                            intervalInfoMap.getOrPut(interval.start) { mutableListOf() }
                                .add(IntervalInfo(transition, start = true))
                            intervalInfoMap.getOrPut(interval.end + 1) { mutableListOf() }
                                .add(IntervalInfo(transition, start = false))
                        }
                    }
                    is EpsilonTransition -> {
                        error("Epsilon transitions should be removed before AtnDisambiguator running")
                    }
                    else -> {}
                }
            }

            // Move over intervals and assign them to the corresponding transitions
            val intervalToSetTransitionPairs = intervalInfoMap.collectIntervalToSetTransitionPairs()

            // Group created intervals by the list of corresponding transitions
            val setTransitionsToIntervals = linkedMapOf<List<SetTransition>, MutableList<Interval>>()
            for (intervalToSetTransitions in intervalToSetTransitionPairs) {
                setTransitionsToIntervals.getOrPut(intervalToSetTransitions.second) { mutableListOf() }
                    .add(intervalToSetTransitions.first)
            }

            // Build the final list:
            //  - Insert other transitions at the correct order
            //  - Merge list of intervals into `IntervalSet`
            return setTransitionsToIntervals.buildDisjointTransitionInfos(transitionOrder)
        }

        private fun SortedMap<Int, MutableList<IntervalInfo>>.collectIntervalToSetTransitionPairs(): MutableList<Pair<Interval, List<SetTransition>>> {
            val intervalToTransitions = mutableListOf<Pair<Interval, List<SetTransition>>>()
            val currentSetTransitions = mutableListOf<SetTransition>()
            var previousValue: Int? = null

            for ((value, intervalInfos) in this) {
                if (previousValue != null && currentSetTransitions.isNotEmpty()) {
                    intervalToTransitions.add(
                        Interval(previousValue, value - 1) to currentSetTransitions.toList()
                    )
                }

                for (intervalInfo in intervalInfos) {
                    if (intervalInfo.start) {
                        currentSetTransitions.add(intervalInfo.setTransition)
                    } else {
                        val isTransitionRemoved = currentSetTransitions.remove(intervalInfo.setTransition)
                        require(isTransitionRemoved)
                    }
                }

                previousValue = value
            }

            return intervalToTransitions
        }

        private fun LinkedHashMap<List<SetTransition>, MutableList<Interval>>.buildDisjointTransitionInfos(
            transitionOrder: MutableMap<Transition, Int>,
        ) : List<DisjointTransitionInfo<*, *>> {
            val processedOtherTransitions = mutableSetOf<Transition>()
            return buildList {
                for ((setTransitions, intervals) in this@buildDisjointTransitionInfos) {
                    val minOrder = setTransitions.minOf { transitionOrder.getValue(it) }
                    addRegularTransitionsAtCurrentOrder(minOrder, transitionOrder, processedOtherTransitions)
                    add(DisjointTransitionInfo(SetTransition::class, IntervalSet(intervals), setTransitions))
                }
                addRegularTransitionsAtCurrentOrder(transitionOrder.size, transitionOrder, processedOtherTransitions)
            }
        }

        private fun MutableList<DisjointTransitionInfo<*, *>>.addRegularTransitionsAtCurrentOrder(
            minOrder: Int,
            transitionOrder: MutableMap<Transition, Int>,
            processedOtherTransitions: MutableSet<Transition>
        ) {
            val otherTransitionsBeforeCurrentOrder = mutableListOf<Transition>()

            for ((transition, order) in transitionOrder) {
                if (order < minOrder) {
                    if (transition !is SetTransition && processedOtherTransitions.add(transition)) {
                        otherTransitionsBeforeCurrentOrder.add(transition)
                    }
                } else {
                    break
                }
            }

            if (otherTransitionsBeforeCurrentOrder.isNotEmpty()) {
                var currentTransitionClass: KClass<out Transition>? = null
                val currentTransitions: MutableList<Transition> = mutableListOf()
                val groupedOtherTransitions: MutableMap<KClass<out Transition>, List<Transition>> =
                    mutableMapOf()

                // Group other sequential transitions of the same type
                for (otherTransitionAtCurrentOrder in otherTransitionsBeforeCurrentOrder) {
                    if (currentTransitionClass != null && otherTransitionAtCurrentOrder::class != currentTransitionClass) {
                        groupedOtherTransitions[currentTransitionClass] = currentTransitions.toList()
                    }
                    currentTransitions.add(otherTransitionAtCurrentOrder)
                    currentTransitionClass = otherTransitionAtCurrentOrder::class
                }

                if (currentTransitionClass != null) {
                    groupedOtherTransitions[currentTransitionClass] = currentTransitions.toList()
                }

                for ((transitionClass, groupedTransitions) in groupedOtherTransitions) {
                    val data: Any? = when (transitionClass) {
                        EndTransition::class -> (groupedTransitions.first() as EndTransition).rule
                        ErrorTransition::class -> groupedTransitions.flatMapTo(LinkedHashSet()) { (it as ErrorTransition).diagnostics }
                        else -> null
                    }
                    add(DisjointTransitionInfo(transitionClass, data, groupedTransitions))
                }
            }
        }

        private fun Transition.bindOrMerge(newSource: State, newTarget: State) {
            val existingTreeNodes = newSource.outTransitions.firstNotNullOfOrNull {
                if (it.checkByInfo(this, disambiguation = true) && it.target === newTarget) it.treeNodes else null
            }
            if (existingTreeNodes == null) {
                clone(newSource, newTarget).bind()
            } else {
                existingTreeNodes.addAll(treeNodes)
            }
        }
    }
}