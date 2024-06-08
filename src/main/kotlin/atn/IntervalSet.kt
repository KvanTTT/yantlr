package atn

data class IntervalSet(val intervals: List<Interval>) {
    constructor(atom: Int) : this(listOf(Interval(atom, atom)))

    constructor(start: Int, end: Int) : this(listOf(Interval(start, end)))

    constructor(range: Interval) : this(listOf(range))

    override fun toString(): String = intervals.joinToString(separator = ", ")
}