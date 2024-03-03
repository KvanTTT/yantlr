open class AntlrTokenStream(val tokens: List<AntlrToken>) {
    private val eofToken: AntlrToken by lazy(LazyThreadSafetyMode.NONE) { AntlrToken(AntlrTokenType.EofRule, 0, 0) }

    open fun getToken(index: Int): AntlrToken = if (index < tokens.size) tokens[index] else eofToken

    open fun createErrorToken(tokenType: AntlrTokenType): AntlrToken =
        AntlrToken(tokenType, 0, 0, AntlrTokenChannel.Error)

    open fun getTokenValue(token: AntlrToken): String? = token.value
}