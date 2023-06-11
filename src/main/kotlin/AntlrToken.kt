class AntlrToken(
    val lexer: AntlrLexer?,
    val type: AntlrTokenType,
    val index: Int,
    val length: Int,
    val channel: AntlrTokenChannel = AntlrTokenChannel.Default,
    val definedValue: String? = null
) {
    companion object {
        fun createAbstractToken(type: AntlrTokenType, channel: AntlrTokenChannel = AntlrTokenChannel.Default, value: String? = null): AntlrToken {
            return AntlrToken(null, type, 0, 0, channel, value)
        }
    }

    val value: String? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        definedValue ?: lexer?.substring(this)
    }

    fun end() = index + length

    override fun toString(): String {
        val result = StringBuilder("AntlrToken($type")
        if (lexer != null) {
            result.append(", $index, ${end()}")
        }
        if (value != null) {
            result.append(", '$value'")
        }
        result.append(")")
        return result.toString()
    }
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