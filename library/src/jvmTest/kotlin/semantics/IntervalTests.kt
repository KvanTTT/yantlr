package semantics

import atn.Interval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object IntervalTests {
    @Test
    fun negateEmpty() {
        assertEquals(Interval.Full, Interval.Empty.negate().single())
    }

    @Test
    fun negateFull() {
        assertTrue(Interval.Full.negate().isEmpty())
    }

    @Test
    fun negateSingle() {
        val negateIntervals = Interval(42).negate()
        assertEquals(2, negateIntervals.size)
        assertEquals(Interval(Interval.MIN, 41), negateIntervals[0])
        assertEquals(Interval(43, Interval.MAX), negateIntervals[1])
    }

    @Test
    fun negateRange() {
        val negateIntervals = Interval(10, 20).negate()
        assertEquals(2, negateIntervals.size)
        assertEquals(Interval(Interval.MIN, 9), negateIntervals[0])
        assertEquals(Interval(21, Interval.MAX), negateIntervals[1])
    }

    @Test
    fun negateRangeToMax() {
        assertEquals(Interval(Interval.MIN, 19), Interval(20, Interval.MAX).negate().single())
    }
}