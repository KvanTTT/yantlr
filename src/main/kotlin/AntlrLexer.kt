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

    private val eofToken: AntlrToken by lazy(LazyThreadSafetyMode.NONE) { createToken(AntlrTokenType.EofRule, text.length, 0, AntlrTokenChannel.Default) }
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

        return when (text[charIndex]) {
            ':' -> tokenizeSingleChar(AntlrTokenType.Colon)
            ';' -> tokenizeSingleChar(AntlrTokenType.Semicolon)
            '|' -> tokenizeSingleChar(AntlrTokenType.Or)
            '*' -> tokenizeSingleChar(AntlrTokenType.Star)
            '+' -> tokenizeSingleChar(AntlrTokenType.Plus)
            '(' -> tokenizeSingleChar(AntlrTokenType.LeftParen)
            ')' -> tokenizeSingleChar(AntlrTokenType.RightParen)
            '\'' -> tokenizeString()
            '\r', '\n' -> tokenizeLineBreak()
            '/' -> tokenizeComment()

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
            in whitespaceChars -> tokenizeSequence(AntlrTokenType.Whitespace, whitespaceChars, AntlrTokenChannel.Hidden)

            else -> tokenizeSingleChar(AntlrTokenType.Error, AntlrTokenChannel.Error)
        }
    }

    private fun tokenizeSingleChar(
        tokenType: AntlrTokenType,
        tokenChannel: AntlrTokenChannel = AntlrTokenChannel.Default
    ) : AntlrToken {
        return createToken(tokenType, charIndex, 1, tokenChannel).also { charIndex++ }
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

        return createToken(tokenTypeConverter(startIndex, tokenType), startIndex, charIndex - startIndex, channel)
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

    private fun tokenizeString(): AntlrToken {
        val startIndex = charIndex
        charIndex++
        var length: Int = -1
        while (charIndex < text.length) {
            val endChar = text[charIndex]
            if (endChar == '\'') {
                length = charIndex - startIndex + 1
                charIndex++
                break
            } else if (checkLineBreak(endChar)) { // Multiline string literals are not supported
                break
            }
            charIndex++
        }

        val channel = if (length == -1) {
            length = charIndex - startIndex
            AntlrTokenChannel.Error
        } else {
            AntlrTokenChannel.Default
        }
        return createToken(AntlrTokenType.String, startIndex, length, channel)
    }

    private fun tokenizeLineBreak(): AntlrToken {
        val startIndex = charIndex
        processLineBreak()
        return createToken(AntlrTokenType.LineBreak, startIndex, charIndex - startIndex, AntlrTokenChannel.Hidden)
    }

    private fun tokenizeComment(): AntlrToken {
        val startIndex = charIndex
        charIndex++
        var tokenType: AntlrTokenType = AntlrTokenType.Error
        if (charIndex < text.length) {
            if (text[charIndex] == '/') {
                charIndex++
                while (charIndex < text.length) {
                    if (checkLineBreak(text[charIndex])) {
                        break
                    }
                    charIndex++
                }
                tokenType = AntlrTokenType.LineComment
            } else if (text[charIndex] == '*') {
                charIndex++
                while (charIndex < text.length) {
                    val endChar = text[charIndex]
                    if (endChar == '*' && checkChar(charIndex + 1, '/')) {
                        charIndex += 2
                        break
                    } else if (checkLineBreak(endChar)) {
                        processLineBreak()
                    } else {
                        charIndex++
                    }
                }
                tokenType = AntlrTokenType.BlockComment
            }
        }
        val tokenChannel = if (tokenType == AntlrTokenType.Error) AntlrTokenChannel.Error else AntlrTokenChannel.Hidden
        return createToken(tokenType, startIndex, charIndex - startIndex, tokenChannel)
    }

    private fun processLineBreak() {
        val c = text[charIndex]
        charIndex++
        if (c == '\r' && checkChar(charIndex, '\n')) {
            charIndex++
        }
        lineIndexes.add(charIndex)
    }

    private fun checkLineBreak(value: Char) = value == '\r' || value == '\n'

    private fun checkChar(index: Int, value: Char): Boolean = index < text.length && text[index] == value

    private fun createToken(tokenType: AntlrTokenType, startIndex: Int, length: Int, channel: AntlrTokenChannel): AntlrToken {
        return AntlrToken(this, tokenType, startIndex, length, channel)
    }
}