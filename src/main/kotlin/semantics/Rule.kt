package semantics

import parser.RuleNode

class Rule(val isLexer: Boolean, val isFragment: Boolean, val references: List<Rule>, val ruleNode: RuleNode)