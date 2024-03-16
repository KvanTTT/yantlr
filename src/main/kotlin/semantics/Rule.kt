package semantics

import parser.RuleNode

class Rule(val name: String, val isLexer: Boolean, val ruleNode: RuleNode)