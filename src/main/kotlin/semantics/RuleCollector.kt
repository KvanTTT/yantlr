package semantics

import AntlrTreeVisitor
import RuleRedefinition
import SemanticsDiagnostics
import parser.*

class RuleCollector(
    val lexer: AntlrLexer,
    val diagnosticReporter: ((SemanticsDiagnostics) -> Unit)? = null
) : AntlrTreeVisitor<Unit>() {
    private val rules = sortedMapOf<String, Rule>()

    fun collect(grammarNode: GrammarNode): Map<String, Rule> {
        rules.clear()
        visitTreeNode(grammarNode)
        return rules
    }

    override fun visitTreeNode(node: AntlrTreeNode) {
        node.acceptChildren(this)
    }

    override fun visitToken(token: AntlrToken) {}

    override fun visitRuleNode(node: RuleNode) {
        val id: String = lexer.getTokenValue(node.lexerOrParserIdToken)
        val existingRule = rules[id]
        if (existingRule != null) {
            diagnosticReporter?.invoke(RuleRedefinition(
                id,
                existingRule.ruleNode.lexerOrParserIdToken.getInterval(),
                node.lexerOrParserIdToken.getInterval()
            ))
        } else {
            rules[id] = Rule(id, node.lexerOrParserIdToken.type == AntlrTokenType.LexerId, node)
        }
    }
}