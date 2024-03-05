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

    override fun visitTreeNode(node: AntlrNode) {
        node.acceptChildren(this)
    }

    override fun visitToken(token: AntlrToken) {}

    override fun visitRuleNode(node: RuleNode) {
        val id: String
        val altNode = node.altNode
        val isLexer = if (altNode is RuleNode.AltLexerIdNode) {
            id = lexer.getTokenValue(altNode.lexerIdToken)!!
            true
        } else {
            id = lexer.getTokenValue((altNode as RuleNode.AltParserIdNode).parserIdToken)!!
            false
        }

        if (rules.containsKey(id)) {
            // TODO: Name collision checking
            throw IllegalStateException("Rule $id already exists")
        } else {
            rules[id] = Rule(node.altNode.toString(), isLexer, node)
        }
    }
}