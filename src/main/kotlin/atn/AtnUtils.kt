package atn

import parser.AntlrNode

fun Transition.clone(newSource: State, newTarget: State, treeNodes: List<AntlrNode> = this.treeNodes): Transition {
    return when (this) {
        is EpsilonTransition -> EpsilonTransition(newSource, newTarget, treeNodes)
        is SetTransition -> SetTransition(set, newSource, newTarget, treeNodes)
        is RuleTransition -> RuleTransition(rule, newSource, newTarget, treeNodes)
        is EndTransition -> EndTransition(rule, newSource, newTarget, treeNodes)
        else -> error("Unknown transition type: $this")
    }
}