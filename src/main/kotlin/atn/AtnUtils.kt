package atn

fun Transition.clone(newSource: State, newTarget: State): Transition {
    return when (this) {
        is EpsilonTransition -> EpsilonTransition(newSource, newTarget, treeNodes)
        is SetTransition -> SetTransition(set, newSource, newTarget, treeNodes)
        is RuleTransition -> RuleTransition(rule, newSource, newTarget, treeNodes)
        is EndTransition -> EndTransition(rule, newSource, newTarget, treeNodes)
        is ErrorTransition -> ErrorTransition(LinkedHashSet(diagnostics), newSource, newTarget, treeNodes)
    }
}

fun Transition.bind(): Transition {
    target.inTransitions.add(this)
    source.outTransitions.add(this)
    return this
}

fun Transition.unbind() {
    target.inTransitions.remove(this)
    source.outTransitions.remove(this)
}

fun State.unbindOuts() {
    outTransitions.forEach { it.target.inTransitions.remove(it) }
    outTransitions.clear()
}

fun Transition.checkByInfo(other: Transition, disambiguation: Boolean = false): Boolean {
    when (this) {
        is EpsilonTransition -> {
            if (other !is EpsilonTransition) return false
        }
        is SetTransition -> {
            if (other !is SetTransition || (if (disambiguation) set != other.set else set !== other.set)) return false
        }
        is RuleTransition -> {
            if (other !is RuleTransition || rule !== other.rule) return false
        }
        is EndTransition -> {
            if (other !is EndTransition || rule !== other.rule) return false
        }
        is ErrorTransition -> {
            if (other !is ErrorTransition) return false
        }
    }

    return if (disambiguation) true else treeNodes === other.treeNodes
}