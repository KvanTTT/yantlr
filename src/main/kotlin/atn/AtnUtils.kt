package atn

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

fun State.unbindOuts() {
    outTransitions.forEach { it.target.inTransitions.remove(it) }
    outTransitions.clear()
}