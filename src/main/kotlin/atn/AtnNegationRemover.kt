package atn

import SemanticsDiagnostic
import parser.ElementNode
import java.util.*

class AtnNegationRemover(val diagnosticReporter: ((SemanticsDiagnostic) -> Unit)? = null) {
    fun run(atn: Atn) {
        fun <T : RootState> run(rootStates: List<T>) = rootStates.forEach { Helper().run(it) }

        run(atn.modeStartStates)
        run(atn.lexerStartStates)
        run(atn.parserStartStates)
    }

    private class Helper {
        private val negationStateMap: MutableMap<ElementNode, State> = mutableMapOf()

        fun run(rootState: RootState) {
            searchNegationStates(rootState)
            performNegation(rootState)
        }

        private fun searchNegationStates(state: State) {
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

        private fun performNegation(state: State) {
            val visitedStates: MutableSet<State> = mutableSetOf()

            fun performNegationInternal(state: State) {
                if (!visitedStates.add(state)) return

                if (state.outTransitions.any { it.data is NegationTransitionData && it.data.negationNode != null }) {
                    val negationInfos = negate(state.outTransitions)

                    // TODO: report a diagnostic when negationInfos is empty

                    // Unbind all out transitions (needed info is kept by `negationInfos`)
                    state.unbindOuts()

                    for (negationInfo in negationInfos) {
                        negationInfo.data.bind(state, negationInfo.targetState)
                    }

                    // If the old target states become orphan, they should be unbound
                    for (negationInfo in negationInfos) {
                        if (negationInfo is Negation) {
                            negationInfo.oldStates.forEach(State::unbindOutsIfNoIns)
                        }
                    }
                }

                state.outTransitions.forEach { performNegationInternal(it.target) }
            }

            performNegationInternal(state)
        }

        private sealed class NegationInfo(val data: TransitionData, val targetState: State)

        private class NoNegation(data: TransitionData, targetState: State) : NegationInfo(data, targetState)

        private class Negation(
            data: TransitionData,
            targetState: State,
            val oldStates: Set<State>,
        ) : NegationInfo(data, targetState)

        private fun negate(outTransitions: List<Transition<*>>): List<NegationInfo> {
            val negationIntervalInfoMap: MutableMap<ElementNode, SortedMap<Int, MutableList<IntervalInfo>>> =
                mutableMapOf()

            val resultNegationInfo = mutableListOf<NegationInfo>()

            for (transition in outTransitions) {
                when (val data = transition.data) {
                    is EpsilonTransitionData -> error("Epsilon transitions should be removed before AtnNegationRemover running")
                    is EndTransitionData -> {
                        resultNegationInfo.add(NoNegation(transition.data, transition.target))
                    }

                    is RuleTransitionData -> {
                        resultNegationInfo.add(NoNegation(transition.data, transition.target))
                        // TODO: Report `Negation on rule`
                    }

                    is IntervalTransitionData -> {
                        @Suppress("UNCHECKED_CAST")
                        transition as Transition<IntervalTransitionData>
                        val interval = data.interval

                        val negationNode = data.negationNode
                        if (negationNode != null) {
                            val negationNodeEnd = negationNode.getInterval().end()
                            val dropRegularTransition = transition.target.outTransitions.any {
                                it.data.antlrNodes.any { antlrNode ->
                                    antlrNode.getInterval().end() > negationNodeEnd
                                }
                            }
                            // If the current transition is the last in negation sequence, it should be removed
                            if (!dropRegularTransition) {
                                IntervalTransitionData(interval, data.antlrNodes, negationNode = null).also {
                                    resultNegationInfo.add(NoNegation(it, transition.target))
                                }
                            }

                            val negateMap = negationIntervalInfoMap.getOrPut(data.negationNode) { sortedMapOf() }

                            val negateTransition = Transition(
                                IntervalTransitionData(interval, data.antlrNodes, negationNode = negationNode),
                                transition.source,
                                transition.target,
                            )

                            negateMap.addIntervalInfo(interval, negateTransition)
                        } else {
                            resultNegationInfo.add(NoNegation(data, transition.target))
                        }
                    }
                }
            }

            val negationIntervalsToTransitionsList: List<Map<Interval, List<Transition<IntervalTransitionData>>>> =
                negationIntervalInfoMap.values.map { it.collectIntervalDataToTransitions(negation = true) }

            negationIntervalsToTransitionsList.forEach { negationIntervalsToTransitions ->
                negationIntervalsToTransitions.forEach { (interval, intervalTransitions) ->
                    val oldStates = intervalTransitions.map { it.target }.toSet()
                    val antlrNodes = intervalTransitions.flatMap { it.data.antlrNodes }.distinct()
                    val firstTransition = intervalTransitions.first()
                    val negationNode = firstTransition.data.negationNode!!
                    require(intervalTransitions.all { it.data.negationNode === negationNode })

                    Negation(
                        IntervalTransitionData(interval, antlrNodes, negationNode = null),
                        negationStateMap.getValue(negationNode),
                        oldStates
                    ).also {
                        resultNegationInfo.add(it) // TODO: probably it's better to insert right after all old transitions
                    }
                }
            }

            return resultNegationInfo
        }
    }
}