package semantics

import parser.RuleNode

class Rule(val isLexer: Boolean, val references: List<Rule>, val ruleNode: RuleNode)