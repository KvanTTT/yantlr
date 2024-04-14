package atn

class IntervalSet(val intervals: List<Interval>) {
    constructor(atom: Int) : this(listOf(Interval(atom, atom)))

    constructor(range: Interval) : this(listOf(range))

    override fun toString(): String = intervals.joinToString(separator = ", ")
}