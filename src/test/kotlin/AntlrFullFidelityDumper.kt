class AntlrFullFidelityDumper(val lexer: AntlrLexer, tokens: List<AntlrToken>) : AntlrTreeVisitor() {
    private val tokensCalculator = AntlrTokensCalculator(tokens)
    private val result = StringBuilder()

    fun dump(node: AntlrNode): String {
        result.clear()
        visitTreeNode(node)
        return result.toString()
    }

    override fun visitTreeNode(node: AntlrNode) {
        node.acceptChildren(this)
    }

    override fun visitToken(token: AntlrToken) {
        with (result) {
            tokensCalculator.getLeadingTokens(token).forEach { append(lexer.getTokenValue(it)) }
            lexer.getTokenValue(token)?.let { append(it) }
            tokensCalculator.getTrailingTokens(token).forEach { append(lexer.getTokenValue(it)) }
        }
    }
}