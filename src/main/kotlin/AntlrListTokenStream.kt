class AntlrListTokenStream(val tokens: List<AntlrToken>) : AntlrTokenStream {
    private val eofToken: AntlrToken by lazy(LazyThreadSafetyMode.NONE) { AntlrToken(AntlrTokenType.EofRule, 0, 0) }

    override fun getToken(index: Int): AntlrToken {
        return if (index < tokens.size) tokens[index] else eofToken
    }

    override fun createErrorTokenAtCurrentIndex(tokenType: AntlrTokenType): AntlrToken {
        return AntlrToken(tokenType, 0, 0, AntlrTokenChannel.Error)
    }

    override fun getTokenValue(token: AntlrToken): String? = token.value
}