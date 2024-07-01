package infrastructure

import AntlrTreeVisitor
import parser.AntlrLexer
import parser.AntlrTreeNode
import parser.AntlrToken
import parser.AntlrTokensCalculator

class AntlrFullFidelityDumper(val lexer: AntlrLexer, tokens: List<AntlrToken>) : AntlrTreeVisitor<Unit, Nothing?>() {
    private val tokensCalculator = AntlrTokensCalculator(tokens)
    private val result = StringBuilder()

    fun dump(node: AntlrTreeNode): String {
        result.clear()
        visitTreeNode(node, null)
        return result.toString()
    }

    override fun visitTreeNode(node: AntlrTreeNode, data: Nothing?) {
        node.acceptChildren(this, data)
    }

    override fun visitToken(token: AntlrToken, data: Nothing?) {
        with (result) {
            tokensCalculator.getLeadingTokens(token).forEach { append(lexer.getTokenValue(it)) }
            append(lexer.getTokenValue(token))
            tokensCalculator.getTrailingTokens(token).forEach { append(lexer.getTokenValue(it)) }
        }
    }
}