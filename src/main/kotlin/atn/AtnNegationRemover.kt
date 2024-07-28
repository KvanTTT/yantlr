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
            if (negationStateMap.isNotEmpty()) {
                performNegation(rootState)
            }
        }

        private fun searchNegationStates(state: State) {
            val visitedStates: MutableSet<State> = mutableSetOf()
            val currentNegationNodes: MutableSet<ElementNode> = mutableSetOf()

            fun searchNegationStatesInternal(state: State) {
                if (!visitedStates.add(state)) return

                val regularTransitions = mutableListOf<Transition<*>>()

                for (outTransition in state.outTransitions) {
                    currentNegationNodes.removeAll {
                        // Check by end offset to handle EndTransition correctly
                        if (outTransition.getEndOffset() > it.getInterval().end()) {
                            negationStateMap.putIfAbsent(it, state)
                            true
                        } else {
                            false
                        }
                    }

                    val negationNodes = outTransition.getNegationNodes()
                    if (negationNodes != null) {
                        currentNegationNodes.addAll(negationNodes)
                        for (negationNode in negationNodes) {
                            // Process negation nodes at first
                            searchNegationStatesInternal(outTransition.target)
                        }
                    } else {
                        regularTransitions.add(outTransition)
                    }
                }

                for (transition in regularTransitions) {
                    searchNegationStatesInternal(transition.target)
                }
            }

            searchNegationStatesInternal(state)
        }

        private fun performNegation(state: State) {
            val visitedStates: MutableSet<State> = mutableSetOf()

            fun performNegationInternal(state: State) {
                if (!visitedStates.add(state)) return

                if (state.outTransitions.any { it.getNegationNodes() != null }) {
                    val negationInfos = negate(state.outTransitions)

                    // TODO: report a diagnostic when negationInfos is empty

                    // Unbind all out transitions (needed info is kept by `negationInfos`)
                    state.unbindOuts()

                    for (negationInfo in negationInfos) {
                        if (negationInfo is RealNegationInfo) {
                            negationInfo.data.bind(state, negationInfo.targetState)
                        }
                    }

                    // If the old target states become orphan, they should be unbound
                    for (negationInfo in negationInfos) {
                        if (negationInfo is Drop) {
                            negationInfo.targetState.unbindOutsIfNoIns()
                        }
                    }
                }

                state.outTransitions.forEach { performNegationInternal(it.target) }
            }

            performNegationInternal(state)
        }

        private sealed class NegationInfo(val targetState: State)

        private sealed class RealNegationInfo(val data: TransitionData, targetState: State) : NegationInfo(targetState)

        private class NoChange(data: TransitionData, targetState: State) : RealNegationInfo(data, targetState)

        private class Negation(data: TransitionData, targetState: State) : RealNegationInfo(data, targetState)

        private class Drop(targetState: State) : NegationInfo(targetState)

        private fun negate(outTransitions: List<Transition<*>>): List<NegationInfo> {
            val negationIntervalInfoMap: MutableMap<ElementNode, SortedMap<Int, MutableList<IntervalInfo>>> =
                mutableMapOf()

            val resultNegationInfo = mutableListOf<NegationInfo>()

            for (transition in outTransitions) {
                when (val data = transition.data) {
                    is EpsilonTransitionData -> error("Epsilon transitions should be removed before AtnNegationRemover running")
                    is EndTransitionData -> {
                        resultNegationInfo.add(NoChange(transition.data, transition.target))
                    }

                    is RuleTransitionData -> {
                        resultNegationInfo.add(NoChange(transition.data, transition.target))
                        // TODO: Report `Negation on rule`
                    }

                    is IntervalTransitionData -> {
                        val interval = data.interval

                        val negationNodes = transition.getNegationNodes()
                        val negationInfo = if (negationNodes != null) {
                            val mainNegationNode = negationNodes.first()
                            val doubleNegation = negationNodes.size % 2 == 0

                            val negateMap = negationIntervalInfoMap.getOrPut(mainNegationNode) { sortedMapOf() }

                            fun Interval.createNegateTransition() = Transition(
                                IntervalTransitionData(this, data.antlrNodes, negationNodes = listOf(mainNegationNode)),
                                transition.source,
                                transition.target,
                            )

                            if (!doubleNegation) {
                                negateMap.addIntervalInfo(interval, interval.createNegateTransition())
                            } else {
                                interval.negate().forEach {
                                    negateMap.addIntervalInfo(it, it.createNegateTransition())
                                }
                            }

                            val negationNodeEnd = mainNegationNode.getInterval().end()
                            val dropRegularTransition = transition.target.outTransitions.any {
                                it.getEndOffset() > negationNodeEnd
                            }

                            // If the current transition is the last in negation sequence, it should be removed
                            if (!dropRegularTransition) {
                                NoChange(IntervalTransitionData(interval, data.antlrNodes, negationNodes = emptyList()), transition.target)
                            } else {
                                Drop(transition.target)
                            }
                        } else {
                            NoChange(data, transition.target)
                        }
                        resultNegationInfo.add(negationInfo)
                    }
                }
            }

            val negationIntervalsToTransitionsList: List<Map<Interval, List<Transition<IntervalTransitionData>>>> =
                negationIntervalInfoMap.values.map { it.collectIntervalDataToTransitions(negation = true) }

            negationIntervalsToTransitionsList.forEach { negationIntervalsToTransitions ->
                negationIntervalsToTransitions.forEach { (interval, intervalTransitions) ->
                    val antlrNodes = intervalTransitions.flatMap { it.data.antlrNodes }.distinct()
                    val firstTransition = intervalTransitions.first()
                    val mainNegationNode = firstTransition.data.negationNodes.first()

                    // TODO: probably it's better to insert right after all old transitions
                    resultNegationInfo.add(
                        Negation(
                            IntervalTransitionData(interval, antlrNodes, negationNodes = emptyList()),
                            negationStateMap.getValue(mainNegationNode),
                        )
                    )
                }
            }

            return resultNegationInfo
        }

        private fun Transition<*>.getEndOffset(): Int {
            return when (data) {
                is EpsilonTransitionData -> error("Epsilon transitions should be removed before AtnNegationRemover running")
                is RealTransitionData -> data.antlrNodes.singleOrNull() ?: error("AtnNegationRemover should be run before AtnDisambiguator")
                is EndTransitionData -> data.rule.treeNode
            }.getInterval().end()
        }
    }
}