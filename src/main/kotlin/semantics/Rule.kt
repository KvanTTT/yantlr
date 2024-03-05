package semantics

import parser.AntlrNode

class Rule(val name: String, val isLexer: Boolean, val treeNode: AntlrNode)