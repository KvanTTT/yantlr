package atn

import parser.AntlrTreeNode
import semantics.Rule

open class State(val transitions: List<Transition>)

class RuleState(
    val rule: Rule,
    val treeNode: AntlrTreeNode,
    val startState: State,
    val endState: State,
) : State(startState.transitions)