package semantics

import parser.AntlrTreeNode
import parser.ModeNode
import parser.RuleNode

abstract class Declaration<T : AntlrTreeNode>(val name: String, val treeNode: T)

class Rule(
    name: String,
    ruleNode: RuleNode,
    val isLexer: Boolean,
    val isFragment: Boolean,
    val isRecursive: Boolean
) : Declaration<RuleNode>(name, ruleNode)

class Mode(
    name: String,
    val modeTreeNode: ModeNode,
    val rules: Map<String, Rule>
) : Declaration<ModeNode>(name, modeTreeNode)