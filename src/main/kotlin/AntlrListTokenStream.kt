class AntlrListTokenStream(tokensList: List<AntlrToken>) : AntlrTokenStream(tokensList) {
    private val eofToken: AntlrToken by lazy(LazyThreadSafetyMode.NONE) { AntlrToken(AntlrTokenType.Eof, 0, 0) }

    override fun getToken(index: Int): AntlrToken = if (index < tokens.size) tokens[index] else eofToken

    override fun getTokenValue(token: AntlrToken): String? = token.value
}