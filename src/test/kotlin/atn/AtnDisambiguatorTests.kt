package atn

import org.junit.jupiter.api.Test
import parser.AntlrNode
import parser.AntlrToken
import parser.AntlrTokenType
import kotlin.test.Ignore
import kotlin.test.assertEquals

object AtnDisambiguatorTests {
    @Test
    fun mergeAntlrNodes() {
        val antlrNode0 = AntlrToken(AntlrTokenType.LexerId, value = "A")
        val antlrNode1 = AntlrToken(AntlrTokenType.LexerId, value = "A")

        val source = State(0)
        val target = State(1)

        val transitions = listOf(
            IntervalTransitionData(Interval('A'.code), sortedSetOf(antlrNode0)).bind(source, target),
            IntervalTransitionData(Interval('A'.code), sortedSetOf(antlrNode1)).bind(source, target)
        )

        val result = AtnDisambiguator().buildDisjointGroups(transitions).single()
        assertEquals(sortedSetOf<AntlrNode>(antlrNode0, antlrNode1), (result.data as IntervalTransitionData).antlrNodes)
    }

    // A* {0} A {1} => A {0, 1} -> A {0}
    @Test
    @Ignore("TODO: https://github.com/KvanTTT/yantlr/issues/5")
    fun mergeEnclosedStates() {
        val source = State(0)
        val target1 = State(1)
        val target2 = State(2)

        val closureAntlrNode = AntlrToken(AntlrTokenType.LexerId, value = "A")
        val atomAntlrNode = AntlrToken(AntlrTokenType.LexerId, value = "A")

        IntervalTransitionData(Interval('A'.code), sortedSetOf(closureAntlrNode)).bind(source, target1)
        IntervalTransitionData(Interval('A'.code), sortedSetOf(closureAntlrNode)).bind(target1, target1)
        IntervalTransitionData(Interval('A'.code), sortedSetOf(atomAntlrNode)).bind(target1, target2)
        IntervalTransitionData(Interval('A'.code), sortedSetOf(atomAntlrNode)).bind(source, target2)

        val atnDisambiguator = AtnDisambiguator(currentStateNumber = 3)
        atnDisambiguator.performDisambiguation(source)

        val outTransition = source.outTransitions.single()
        val outTransitionData = outTransition.data as IntervalTransitionData

        assertEquals(Interval('A'.code), outTransitionData.interval)
        assertEquals(sortedSetOf<AntlrNode>(closureAntlrNode, atomAntlrNode), outTransitionData.antlrNodes)

        val newOutTransition = outTransition.target.outTransitions.single()

        assertEquals(sortedSetOf<AntlrNode>(closureAntlrNode), (newOutTransition.data as IntervalTransitionData).antlrNodes)
        assertEquals(newOutTransition.source, newOutTransition.target)
    }
}