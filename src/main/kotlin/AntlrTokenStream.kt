interface AntlrTokenStream {
    fun getToken(index: Int): AntlrToken

    fun createErrorTokenAtCurrentIndex(tokenType: AntlrTokenType): AntlrToken

    fun getTokenValue(token: AntlrToken): String?
}