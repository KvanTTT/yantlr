package semantics

import parser.RuleNode

class Rule(val isLexer: Boolean, val isFragment: Boolean, val isRecursive: Boolean, val ruleNode: RuleNode)