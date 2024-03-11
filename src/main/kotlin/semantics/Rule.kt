package semantics

import parser.AntlrTreeNode

class Rule(val name: String, val isLexer: Boolean, val treeNode: AntlrTreeNode)