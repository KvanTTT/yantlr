package semantics

import AntlrTreeVisitor
import parser.*

class RuleCollector(val lexer: AntlrLexer) : AntlrTreeVisitor<Unit>() {
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
        if (rules.containsKey(id)) {
            // TODO: Name collision checking
            throw IllegalStateException("Rule $id already exists")
        } else {
            rules[id] = Rule(id, node.lexerOrParserIdToken.type == AntlrTokenType.LexerId, node)
        }
    }
}