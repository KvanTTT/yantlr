class AntlrToken(
    val type: AntlrTokenType,
    val offset: Int = 0,
    val length: Int = 0,
    val channel: AntlrTokenChannel = AntlrTokenChannel.Default,
    val value: String? = null
) {
    companion object {
        fun createAbstractToken(type: AntlrTokenType, channel: AntlrTokenChannel = AntlrTokenChannel.Default, value: String? = null): AntlrToken {
            return AntlrToken(type, 0, 0, channel, value)
        }
    }

    fun end() = offset + length
}

enum class AntlrTokenType {
    EofRule,
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
    LineComment,
    BlockComment,
    Error,
    Fragment,
    Import,
    Mode,
    Channels,
    Options,
    Tokens,
    Equals,
    Range,
    Dot,
    Pound,
    RightArrow,
    Tilde,
    Question,
    Comma,
    PlusAssign,
    Bom,
    RightBrace,
    LeftBracket,
    RightBracket,
    Hyphen,
    Char,
    EscapedChar,
    Quote,
    UnicodeEscapedChar,
}

enum class AntlrTokenChannel {
    Default,
    Hidden,
    Error
}

enum class AntlrMode {
    Default,
    StringLiteral,
    CharSetLiteral
}