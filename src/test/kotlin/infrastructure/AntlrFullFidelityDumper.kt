package infrastructure

import AntlrTreeVisitor
import parser.AntlrLexer
import parser.AntlrTreeNode
import parser.AntlrToken
import parser.AntlrTokensCalculator

class AntlrFullFidelityDumper(val lexer: AntlrLexer, tokens: List<AntlrToken>) : AntlrTreeVisitor<Unit>() {
    private val tokensCalculator = AntlrTokensCalculator(tokens)
    private val result = StringBuilder()

    fun dump(node: AntlrTreeNode): String {
        result.clear()
        visitTreeNode(node)
        return result.toString()
    }

    override fun visitTreeNode(node: AntlrTreeNode) {
        node.acceptChildren(this)
    }

    override fun visitToken(token: AntlrToken) {
        with (result) {
            tokensCalculator.getLeadingTokens(token).forEach { append(lexer.getTokenValue(it)) }
            append(lexer.getTokenValue(token))
            tokensCalculator.getTrailingTokens(token).forEach { append(lexer.getTokenValue(it)) }
        }
    }
}