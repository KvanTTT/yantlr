package atn

import java.util.*

fun TransitionData.bind(source: State, target: State): Transition<*> {
    return Transition(this, source, target).also {
        target.inTransitions.add(it)
        source.outTransitions.add(it)
    }
}

fun Transition<*>.unbind() {
    require(target.inTransitions.remove(this))
    require(source.outTransitions.remove(this))
}

fun State.unbindOutsIfNoIns() {
    if (inTransitions.all { it.isEnclosed }) {
        unbindOuts()
    }
}

fun State.unbindOuts() {
    outTransitions.forEach { it.target.inTransitions.remove(it) }
    outTransitions.clear()
}

data class IntervalInfo(val setTransition: Transition<IntervalTransitionData>, val start: Boolean)

fun SortedMap<Int, MutableList<IntervalInfo>>.addIntervalInfo(interval: Interval, transition: Transition<IntervalTransitionData>) {
    getOrPut(interval.start) { mutableListOf() }.add(IntervalInfo(transition, start = true))
    getOrPut(interval.end + 1) { mutableListOf() }.add(IntervalInfo(transition, start = false))
}

fun SortedMap<Int, MutableList<IntervalInfo>>.collectIntervalDataToTransitions(negation: Boolean)
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