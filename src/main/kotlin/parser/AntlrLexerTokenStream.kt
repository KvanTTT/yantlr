package parser

class AntlrLexerTokenStream(private val lexer: AntlrLexer) : AntlrTokenStream(mutableListOf()) {
    private val appendableTokens = tokens as MutableList<AntlrToken>

    override fun getToken(index: Int): AntlrToken {
        while (index >= tokens.size) {
            val nextToken = lexer.nextToken()
            if (nextToken.type == AntlrTokenType.Eof) {
                if (appendableTokens.lastOrNull()?.type != AntlrTokenType.Eof) {
                    appendableTokens.add(nextToken) // Only single Eof token is allowed at the end
                }
                return nextToken
            }
            appendableTokens.add(nextToken)
        }
        return appendableTokens[index]
    }

    override fun getTokenValue(token: AntlrToken): String = lexer.getTokenValue(token)

    override fun fetchAllTokens(): List<AntlrToken> {
        var index = 0
        while (getToken(index).type != AntlrTokenType.Eof) {
            index++
        }

        return tokens
    }
}