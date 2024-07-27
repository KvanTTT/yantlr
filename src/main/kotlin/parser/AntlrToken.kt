package parser

import SourceInterval

class AntlrToken(
    val type: AntlrTokenType,
    val offset: Int = -1,
    val length: Int = -1,
    val channel: AntlrTokenChannel = AntlrTokenChannel.Default,
    val value: String? = null
): AntlrNode() {
    fun end() = offset + length

    override fun getInterval(): SourceInterval = SourceInterval(offset, length)

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
    Digit,
    Empty, // Special token type needed for preserving location of empty alternatives
}

val fixedTokenValues: Map<AntlrTokenType, String> = mapOf(
    AntlrTokenType.Lexer to "lexer",
    AntlrTokenType.Parser to "parser",
    AntlrTokenType.Grammar to "grammar",
    AntlrTokenType.Colon to ":",
    AntlrTokenType.Semicolon to ";",
    AntlrTokenType.Bar to "|",
    AntlrTokenType.Star to "*",
    AntlrTokenType.Plus to "+",
    AntlrTokenType.LeftParen to "(",
    AntlrTokenType.RightParen to ")",
    AntlrTokenType.Fragment to "fragment",
    AntlrTokenType.Import to "import",
    AntlrTokenType.Mode to "mode",
    AntlrTokenType.Channels to "channels",
    AntlrTokenType.Options to "options",
    AntlrTokenType.Tokens to "tokens",
    AntlrTokenType.Equals to "=",
    AntlrTokenType.Range to "..",
    AntlrTokenType.Dot to ".",
    AntlrTokenType.Pound to "#",
    AntlrTokenType.RightArrow to "->",
    AntlrTokenType.Tilde to "~",
    AntlrTokenType.Question to "?",
    AntlrTokenType.Comma to ",",
    AntlrTokenType.PlusAssign to "+=",
    AntlrTokenType.Bom to "\uFEFF",
    AntlrTokenType.RightBrace to "}",
    AntlrTokenType.LeftBracket to "[",
    AntlrTokenType.RightBracket to "]",
    AntlrTokenType.Hyphen to "-",
    AntlrTokenType.Quote to "'",
    AntlrTokenType.Empty to "",
)
