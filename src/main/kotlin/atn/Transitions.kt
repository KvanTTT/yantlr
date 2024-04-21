package atn

import parser.AntlrNode
import semantics.Rule

abstract class Transition(val target: State, val treeNodes: List<AntlrNode>)

class EpsilonTransition(target: State, treeNodes: List<AntlrNode>) : Transition(target, treeNodes)

class SetTransition(val set: IntervalSet, target: State, treeNodes: List<AntlrNode>) : Transition(target, treeNodes)

class RuleTransition(val rule: Rule, target: State, treeNodes: List<AntlrNode>) : Transition(target, treeNodes)