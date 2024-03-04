package parser

abstract class AntlrTokenStream(val tokens: List<AntlrToken>) {
    abstract fun getToken(index: Int): AntlrToken

    abstract fun getTokenValue(token: AntlrToken): String?
}