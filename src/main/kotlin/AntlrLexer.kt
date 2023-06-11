class AntlrLexer(val text: String) {
    companion object {
        private val whitespaceChars = setOf(' ', '\t')
        private val lexerIdStartChars = charSetOf('A'..'Z')
        private val parserIdStartChars = charSetOf('a'..'z')
        private val idContinueChars = charSetOf('a'..'z', 'A'..'Z', '0'..'9', '_'..'_')

        private fun charSetOf(vararg charRanges: CharRange): Set<Char> {
            val result = mutableSetOf<Char>()
            for (charRange in charRanges) {
                for (c in charRange) {
                    result.add(c)
                }
            }
            return result
        }
    }

    private val lineIndexes = mutableListOf<Int>()

    private val eofToken: AntlrToken by lazy(LazyThreadSafetyMode.NONE) { AntlrToken(this, AntlrTokenType.EofRule, text.length, 0) }
    var charIndex: Int = 0
        private set
        get

    fun substring(token: AntlrToken): String {
        return text.substring(token.index, token.end())
    }

    fun lineColumn(i: Int): Pair<Int, Int> {
        lineIndexes.binarySearch { it.compareTo(i) }.let { lineIndex ->
            return if (lineIndex < 0) {
                val line = -lineIndex - 2
                Pair(line + 1, i - lineIndexes[line] + 1)
            } else {
                Pair(lineIndex + 1, 1)
            }
        }
    }

    fun nextToken(): AntlrToken {
        if (charIndex == 0) {
            lineIndexes.add(0)
        }
        if (charIndex >= text.length) {
            return eofToken
        }

        val c = text[charIndex]
        return when (c) {
            ':' -> tokenizeSingleCharToken(AntlrTokenType.Colon)
            ';' -> tokenizeSingleCharToken(AntlrTokenType.Semicolon)
            '|' -> tokenizeSingleCharToken(AntlrTokenType.Or)
            '*' -> tokenizeSingleCharToken(AntlrTokenType.Star)
            '+' -> tokenizeSingleCharToken(AntlrTokenType.Plus)
            '(' -> tokenizeSingleCharToken(AntlrTokenType.LeftParen)
            ')' -> tokenizeSingleCharToken(AntlrTokenType.RightParen)

            '\'' -> {
                val startIndex = charIndex
                charIndex++
                var stringToken: AntlrToken? = null
                while (charIndex < text.length) {
                    val endChar = text[charIndex]
                    if (endChar == '\'') {
                        stringToken = AntlrToken(this, AntlrTokenType.String, startIndex, charIndex - startIndex + 1)
                        charIndex++
                        break
                    } else if (endChar == '\r' || endChar == '\n') {
                        break
                    }
                    charIndex++
                }
                if (stringToken == null) {
                    stringToken =
                        AntlrToken(this, AntlrTokenType.String, startIndex, charIndex - startIndex, AntlrTokenChannel.Error)
                }
                stringToken
            }
            '\r' -> {
                val startIndex = charIndex
                charIndex++
                if (charIndex < text.length && text[charIndex] == '\n') {
                    charIndex++
                }
                lineIndexes.add(charIndex)
                AntlrToken(this, AntlrTokenType.LineBreak, startIndex, charIndex - startIndex, AntlrTokenChannel.Whitespace)
            }
            '\n' -> tokenizeSingleCharToken(AntlrTokenType.LineBreak, AntlrTokenChannel.Whitespace).also { lineIndexes.add(charIndex) }

            in lexerIdStartChars -> tokenizeSequence(AntlrTokenType.LexerId, idContinueChars)
            in parserIdStartChars -> tokenizeSequence(AntlrTokenType.ParserId, idContinueChars,
                tokenTypeConverter = { startIndex, type ->
                    when {
                        checkKeyword(startIndex, "lexer") -> AntlrTokenType.Lexer
                        checkKeyword(startIndex, "parser") -> AntlrTokenType.Parser
                        checkKeyword(startIndex, "grammar") -> AntlrTokenType.Grammar
                        else -> type
                    }
            })
            in whitespaceChars -> tokenizeSequence(AntlrTokenType.Whitespace, whitespaceChars, AntlrTokenChannel.Whitespace)

            else -> tokenizeSingleCharToken(AntlrTokenType.Error, AntlrTokenChannel.Error)
        }
    }

    private fun tokenizeSingleCharToken(
        tokenType: AntlrTokenType,
        tokenChannel: AntlrTokenChannel = AntlrTokenChannel.Default
    ) : AntlrToken {
        return AntlrToken(this, tokenType, charIndex, 1, tokenChannel).also { charIndex++ }
    }

    private inline fun tokenizeSequence(
        tokenType: AntlrTokenType,
        continueChars: Set<Char>,
        channel: AntlrTokenChannel = AntlrTokenChannel.Default,
        crossinline tokenTypeConverter: (Int, AntlrTokenType) -> AntlrTokenType = { _, type -> type }
    ): AntlrToken {
        val startIndex = charIndex
        charIndex++
        while (charIndex < text.length && text[charIndex] in continueChars) {
            charIndex++
        }

        return AntlrToken(this, tokenTypeConverter(startIndex, tokenType), startIndex, charIndex - startIndex, channel)
    }

    private fun checkKeyword(start: Int, value: String): Boolean {
        if (value.length < charIndex - start) { // Only strict match is relevant
            return false
        }
        for (i in value.indices) {
            if (start + i == text.length || text[start + i] != value[i]) {
                return false
            }
        }
        return true
    }
}