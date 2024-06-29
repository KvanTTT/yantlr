package atn

import org.junit.jupiter.api.Test
import parser.AntlrToken
import parser.AntlrTokenType
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

object AtnDisambiguatorTests {
    @Test
    fun mergeAntlrNodes() {
        val antlrNode0 = AntlrToken(AntlrTokenType.LexerId, value = "A")
        val antlrNode1 = AntlrToken(AntlrTokenType.LexerId, value = "A")

        val source = State(0)
        val target = State(1)

        val transitions = listOf(
            IntervalTransitionData(Interval('A'.code), listOf(antlrNode0)).bind(source, target),
            IntervalTransitionData(Interval('A'.code), listOf(antlrNode1)).bind(source, target)
        )

        val result = AtnDisambiguator().buildDisjointGroups(transitions).single()
        assertEquals(listOf(antlrNode0, antlrNode1), result.data.antlrNodes)
    }

    // A {0} | A {1} => A {0, 1}
    @Test
    fun mergeRegularStates() {
        val source = State(0)
        val regular0State = State(1)
        val regular1State = State(2)

        val regular0AntlrNode = AntlrToken(AntlrTokenType.LexerId, value = "A")
        val regular1AntlrNode = AntlrToken(AntlrTokenType.LexerId, value = "A")

        IntervalTransitionData(Interval('A'.code), listOf(regular0AntlrNode)).bind(source, regular0State)
        IntervalTransitionData(Interval('A'.code), listOf(regular1AntlrNode)).bind(source, regular1State)

        val atnDisambiguator = AtnDisambiguator(currentStateNumber = 3)
        atnDisambiguator.performDisambiguation(source)

        val outTransition0Data = source.outTransitions.single().data as IntervalTransitionData
        assertEquals(Interval('A'.code), outTransition0Data.interval)
        assertEquals(listOf(regular0AntlrNode, regular1AntlrNode), outTransition0Data.antlrNodes)
    }

    // A {0} | [A-Z]+ {1} => A {0, 1} | [B-Z]+ {1}
    @Test
    fun mergeRegularAndClosureState() {
        val source = State(0)
        val regularState = State(1)
        val closureState = State(2)

        val regularAntlrNode = AntlrToken(AntlrTokenType.LexerId, value = "A")
        val closureAntlrNode = AntlrToken(AntlrTokenType.Range, value = "A..Z")

        IntervalTransitionData(Interval('A'.code), listOf(regularAntlrNode)).bind(source, regularState)
        IntervalTransitionData(Interval('A'.code, 'Z'.code), listOf(closureAntlrNode)).bind(source, closureState)
        IntervalTransitionData(Interval('A'.code, 'Z'.code), listOf(closureAntlrNode)).bind(closureState, closureState)

        val atnDisambiguator = AtnDisambiguator(currentStateNumber = 3)
        atnDisambiguator.performDisambiguation(source)

        assertEquals(2, source.outTransitions.size)
        assertNotEquals(regularState, source.outTransitions[0].target) // New state
        assertEquals(closureState, source.outTransitions[1].target) // Old state
        val outTransition0Data =  source.outTransitions[0].data as IntervalTransitionData
        val outTransition1Data = source.outTransitions[1].data as IntervalTransitionData

        assertEquals(Interval('A'.code), outTransition0Data.interval)
        assertEquals(listOf(regularAntlrNode, closureAntlrNode), outTransition0Data.antlrNodes)

        assertEquals(Interval('B'.code, 'Z'.code), outTransition1Data.interval)
        assertEquals(listOf(closureAntlrNode), outTransition1Data.antlrNodes)

        val newState = source.outTransitions[0].target
        val newStateOut = newState.outTransitions.single()
        assertEquals(newState, newStateOut.target)
        assertEquals(listOf(closureAntlrNode), newStateOut.data.antlrNodes)
        assertEquals(Interval('A'.code, 'Z'.code), (newStateOut.data as IntervalTransitionData).interval)
    }

    // A+ | A*
    @Test
    fun mergeClosureAndClosureState() {
        val source = State(0)
        val closurePlusState = State(1)
        val closureStarState = State(2)

        val closurePlusAntlrNode = AntlrToken(AntlrTokenType.Range, value = "A..Z")
        val closureStarAntlrNode = AntlrToken(AntlrTokenType.Range, value = "A..Z")

        IntervalTransitionData(Interval('A'.code, 'Z'.code), listOf(closurePlusAntlrNode)).bind(source, closurePlusState)
        IntervalTransitionData(Interval('A'.code, 'Z'.code), listOf(closurePlusAntlrNode)).bind(closurePlusState, closurePlusState)
        IntervalTransitionData(Interval('A'.code, 'Z'.code), listOf(closureStarAntlrNode)).bind(source, closureStarState)
        IntervalTransitionData(Interval('A'.code, 'Z'.code), listOf(closureStarAntlrNode)).bind(closureStarState, closureStarState)

        val atnDisambiguator = AtnDisambiguator(currentStateNumber = 3)
        atnDisambiguator.performDisambiguation(source)

        val outTransition = source.outTransitions.single()
        val outTransition0Data = outTransition.data as IntervalTransitionData

        assertEquals(Interval('A'.code, 'Z'.code), outTransition0Data.interval)
        assertEquals(listOf(closurePlusAntlrNode, closureStarAntlrNode), outTransition0Data.antlrNodes)

        val newOutTransitions = outTransition.target.outTransitions
        val newOutTransition0 = newOutTransitions[0]
        val newOutTransition1 = newOutTransitions[1]

        assertEquals(listOf(closurePlusAntlrNode), (newOutTransition0.data as IntervalTransitionData).antlrNodes)
        assertEquals(newOutTransition0.source, newOutTransition0.target)

        assertEquals(listOf(closureStarAntlrNode), (newOutTransition1.data as IntervalTransitionData).antlrNodes)
        assertEquals(newOutTransition1.source, newOutTransition1.target)

        // Merge transitions with the same data, but different antlr nodes
        atnDisambiguator.performDisambiguation(outTransition.target)

        val mergedTransition = outTransition.target.outTransitions.single()
        assertEquals(listOf(closurePlusAntlrNode, closureStarAntlrNode), (mergedTransition.data as IntervalTransitionData).antlrNodes)
        assertEquals(outTransition.target, mergedTransition.source)
        assertEquals(mergedTransition.source, mergedTransition.target)
    }

    // A* {0} A {1} => A {0, 1} -> A {0}
    @Test
    fun mergeEnclosedStates() {
        val source = State(0)
        val target1 = State(1)
        val target2 = State(2)

        val closureAntlrNode = AntlrToken(AntlrTokenType.LexerId, value = "A")
        val atomAntlrNode = AntlrToken(AntlrTokenType.LexerId, value = "A")

        IntervalTransitionData(Interval('A'.code), listOf(closureAntlrNode)).bind(source, target1)
        IntervalTransitionData(Interval('A'.code), listOf(closureAntlrNode)).bind(target1, target1)
        IntervalTransitionData(Interval('A'.code), listOf(atomAntlrNode)).bind(target1, target2)
        IntervalTransitionData(Interval('A'.code), listOf(atomAntlrNode)).bind(source, target2)

        val atnDisambiguator = AtnDisambiguator(currentStateNumber = 3)
        atnDisambiguator.performDisambiguation(source)

        val outTransition = source.outTransitions.single()
        val outTransitionData = outTransition.data as IntervalTransitionData

        assertEquals(Interval('A'.code), outTransitionData.interval)
        assertEquals(listOf(closureAntlrNode, atomAntlrNode), outTransitionData.antlrNodes)

        val newOutTransition = outTransition.target.outTransitions.single()

        assertEquals(listOf(closureAntlrNode), (newOutTransition.data as IntervalTransitionData).antlrNodes)
        assertEquals(newOutTransition.source, newOutTransition.target)
    }
}