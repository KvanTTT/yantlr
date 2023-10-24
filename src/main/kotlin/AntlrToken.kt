class AntlrToken(
    val type: AntlrTokenType,
    val index: Int,
    val length: Int,
    val channel: AntlrTokenChannel = AntlrTokenChannel.Default,
    val value: String? = null
) {
    companion object {
        fun createAbstractToken(type: AntlrTokenType, channel: AntlrTokenChannel = AntlrTokenChannel.Default, value: String? = null): AntlrToken {
            return AntlrToken(type, 0, 0, channel, value)
        }
    }

    fun end() = index + length
}

enum class AntlrTokenType {
    EofRule,
    String,
    LexerId,
    ParserId,
    Lexer,
    Parser,
    Grammar,
    Colon,
    Semicolon,
    Or,
    Star,
    Plus,
    LeftParen,
    RightParen,
    Whitespace,
    LineBreak,
    LineComment,
    BlockComment,
    Error
}

enum class AntlrTokenChannel {
    Default,
    Hidden,
    Error
}