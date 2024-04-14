package atn

import parser.AntlrNode
import semantics.Rule

abstract class Transition(val target: State, val targetTreeNodes: List<AntlrNode>)

class EpsilonTransition(target: State, targetTreeNodes: List<AntlrNode>) : Transition(target, targetTreeNodes)

class SetTransition(val set: IntervalSet, target: State, targetTreeNodes: List<AntlrNode>) : Transition(target, targetTreeNodes)

class RuleTransition(val rule: Rule, target: State, targetTreeNodes: List<AntlrNode>) : Transition(target, targetTreeNodes)