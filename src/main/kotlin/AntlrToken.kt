class AntlrToken(
    private val text: String,
    val type: AntlrTokenType,
    val index: Int,
    val length: Int,
    val channel: AntlrTokenChannel = AntlrTokenChannel.Default
) {
    val value: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        text.substring(index, index + length)
    }

    val end = index + length

    override fun toString(): String {
        return "AntlrToken($type, [$index, $end), '$value')"
    }
}

enum class AntlrTokenType {
    Eof,
    String,
    LexerId,
    ParserId,
    Lexer,
    Parser,
    Grammar,
    Colon,
    Semicolon,
    Bar,
    Star,
    Plus,
    LeftParen,
    RightParen,
    Whitespace,
    LineBreak,
    Error
}

enum class AntlrTokenChannel {
    Default,
    Whitespace,
    Error
}