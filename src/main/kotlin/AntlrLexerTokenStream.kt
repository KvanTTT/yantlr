class AntlrLexerTokenStream(private val lexer: AntlrLexer) : AntlrTokenStream {
    private val tokens = mutableListOf<AntlrToken>()

    override fun getToken(index: Int): AntlrToken {
        while (index >= tokens.size) {
            tokens.add(lexer.nextToken())
        }
        return tokens[index]
    }

    override fun createErrorTokenAtCurrentIndex(tokenType: AntlrTokenType): AntlrToken {
        return AntlrToken(lexer, tokenType, lexer.charIndex, 0, AntlrTokenChannel.Error)
    }
}