class AntlrLexerTokenStream(private val lexer: AntlrLexer) : AntlrTokenStream(mutableListOf()) {
    private val appendableTokens = tokens as MutableList<AntlrToken>

    override fun getToken(index: Int): AntlrToken {
        while (index >= tokens.size) {
            appendableTokens.add(lexer.nextToken())
        }
        return appendableTokens[index]
    }

    override fun getTokenValue(token: AntlrToken): String? = lexer.getTokenValue(token)
}