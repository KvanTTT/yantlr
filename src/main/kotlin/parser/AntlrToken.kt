package parser

class AntlrToken(
    val type: AntlrTokenType,
    val offset: Int = -1,
    val length: Int = -1,
    val channel: AntlrTokenChannel = AntlrTokenChannel.Default,
    val value: String? = null
) {
    fun end() = offset + length

    override fun toString(): String {
        val result = StringBuilder()
        result.append("AntlrToken(type=$type")
        if (offset != -1) result.append(", offset=$offset")
        if (length != -1) result.append(", length=$length")
        if (channel != AntlrTokenChannel.Default) result.append(", channel=$channel")
        if (value != null) result.append(", value=$value")
        result.append(")")
        return result.toString()
    }
}

enum class AntlrTokenType {
    Eof,
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
    Quote,
    Char,
    EscapedChar,
    UnicodeEscapedChar,
}
