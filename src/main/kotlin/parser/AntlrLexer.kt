package parser

import UnrecognizedToken

class AntlrLexer(
    val text: String,
    val initializeTokenValue: Boolean = false,
    val diagnosticReporter: ((UnrecognizedToken) -> Unit)? = null
) {
    companion object {
        private val whitespaceChars = setOf(' ', '\t')
        private val idStartChars = charSetOf(
            'a'..'z',
            'A'..'Z',
            '\u00C0'..'\u00D6',
            '\u00D8'..'\u00F6',
            '\u00F8'..'\u02FF',
            '\u0370'..'\u037D',
            '\u037F'..'\u1FFF',
            '\u200C'..'\u200D',
            '\u2070'..'\u218F',
            '\u2C00'..'\u2FEF',
            '\u3001'..'\uD7FF',
            '\uF900'..'\uFDCF',
            '\uFDF0'..'\uFEFE',
            '\uFF00'..'\uFFFD',
        )
        private val idContinueChars = idStartChars + charSetOf(
            '0'..'9',
            '_'..'_',
            '\u00B7'..'\u00B7',
            '\u0300'..'\u036F',
            '\u203F'..'\u2040',
        )
        private val hexDigits = charSetOf('0'..'9', 'a'..'f', 'A'..'F')

        private val fragmentKeyword = Keyword(AntlrTokenType.Fragment, "fragment")
        private val grammarKeyword = Keyword(AntlrTokenType.Grammar, "grammar")
        private val importKeyword = Keyword(AntlrTokenType.Import, "import")
        private val lexerKeyword = Keyword(AntlrTokenType.Lexer, "lexer")
        private val modeKeyword = Keyword(AntlrTokenType.Mode, "mode")
        private val parserKeyword = Keyword(AntlrTokenType.Parser, "parser")

        // Keywords like `options  {` are treated as single token (including whitespaces and '{')
        private val channelsKeyword = Keyword(AntlrTokenType.Channels, "channels", tokenizeToLeftBrace = true)
        private val optionsKeyword = Keyword(AntlrTokenType.Options, "options", tokenizeToLeftBrace = true)
        private val tokensKeyword = Keyword(AntlrTokenType.Tokens, "tokens", tokenizeToLeftBrace = true)

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

    data class Keyword(val tokenType: AntlrTokenType, val value: String, val tokenizeToLeftBrace: Boolean = false)

    private val lineIndexes = mutableListOf<Int>()

    private val eofToken: AntlrToken by lazy(LazyThreadSafetyMode.NONE) {
        createToken(AntlrTokenType.Eof, text.length, 0, AntlrTokenChannel.Default)
    }

    var charIndex: Int = 0
        private set
        get

    fun getTokenValue(token: AntlrToken): String {
        return if (token.type == AntlrTokenType.Eof) {
            ""
        } else {
            token.value ?: text.substring(token.offset, token.end())
        }
    }

    fun getLineColumn(offset: Int): LineColumn = lineIndexes.getLineColumn(offset)

    fun getOffset(lineColumn: LineColumn): Int = lineColumn.getOffset(lineIndexes)

    private var currentMode: AntlrMode = AntlrMode.Default

    fun nextToken(): AntlrToken {
        if (charIndex == 0) {
            lineIndexes.add(0)
        }
        if (charIndex >= text.length) {
            return eofToken
        }

        val char = text[charIndex]
        return when (currentMode) {
            AntlrMode.Default -> nextDefaultToken(char)
            AntlrMode.CharSetLiteral -> nextStringLiteralToken(char, charSet = true)
            AntlrMode.StringLiteral -> nextStringLiteralToken(char, charSet = false)
        }
    }

    private fun nextDefaultToken(char: Char): AntlrToken {
        return when (char) {
            ',' -> tokenizeSingleChar(AntlrTokenType.Comma)
            ':' -> tokenizeSingleChar(AntlrTokenType.Colon)
            ';' -> tokenizeSingleChar(AntlrTokenType.Semicolon)
            '|' -> tokenizeSingleChar(AntlrTokenType.Bar)
            '*' -> tokenizeSingleChar(AntlrTokenType.Star)
            '+' -> {
                if (checkChar(charIndex + 1, '=')) {
                    tokenizeDoubleChar(AntlrTokenType.PlusAssign)
                } else {
                    tokenizeSingleChar(AntlrTokenType.Plus)
                }
            }
            '(' -> tokenizeSingleChar(AntlrTokenType.LeftParen)
            ')' -> tokenizeSingleChar(AntlrTokenType.RightParen)
            '=' -> tokenizeSingleChar(AntlrTokenType.Equals)
            '}' -> tokenizeSingleChar(AntlrTokenType.RightBrace)
            '?' -> tokenizeSingleChar(AntlrTokenType.Question)
            '~' -> tokenizeSingleChar(AntlrTokenType.Tilde)
            '#' -> tokenizeSingleChar(AntlrTokenType.Pound)
            '.' -> {
                if (checkChar(charIndex + 1, '.')) {
                    tokenizeDoubleChar(AntlrTokenType.Range)
                } else {
                    tokenizeSingleChar(AntlrTokenType.Dot)
                }
            }
            '-' -> {
                if (checkChar(charIndex + 1, '>')) {
                    tokenizeDoubleChar(AntlrTokenType.RightArrow)
                } else {
                    tokenizeErrorChar()
                }
            }

            '\r', '\n' -> tokenizeLineBreak()

            '[' -> tokenizeSingleChar(AntlrTokenType.LeftBracket).also { currentMode = AntlrMode.CharSetLiteral }
            '\'' -> tokenizeSingleChar(AntlrTokenType.Quote).also { currentMode = AntlrMode.StringLiteral }

            'c' -> tokenizeKeywordOrId(channelsKeyword)
            'f' -> tokenizeKeywordOrId(fragmentKeyword)
            'g' -> tokenizeKeywordOrId(grammarKeyword)
            'i' -> tokenizeKeywordOrId(importKeyword)
            'l' -> tokenizeKeywordOrId(lexerKeyword)
            'm' -> tokenizeKeywordOrId(modeKeyword)
            'o' -> tokenizeKeywordOrId(optionsKeyword)
            'p' -> tokenizeKeywordOrId(parserKeyword)
            't' -> tokenizeKeywordOrId(tokensKeyword)

            '/' -> tokenizeComment()

            ' ', '\t' -> tokenizeSequence(AntlrTokenType.Whitespace, whitespaceChars, AntlrTokenChannel.Hidden)

            '\uFEFF' -> createToken(AntlrTokenType.Bom, charIndex, 1, AntlrTokenChannel.Hidden).also { charIndex++ }

            in idStartChars -> tokenizeSequence(
                if (Character.isUpperCase(getChar(charIndex))) AntlrTokenType.LexerId else AntlrTokenType.ParserId,
                idContinueChars
            )

            else -> tokenizeSingleChar(AntlrTokenType.Error, AntlrTokenChannel.Error)
        }
    }

    private fun nextStringLiteralToken(char: Char, charSet: Boolean): AntlrToken {
        return when (char) {
            '-' -> if (charSet) {
                tokenizeSingleChar(AntlrTokenType.Hyphen)
            } else {
                tokenizeSingleChar(AntlrTokenType.Char)
            }
            ']' -> if (charSet) {
                tokenizeSingleChar(AntlrTokenType.RightBracket).also { currentMode = AntlrMode.Default }
            } else {
                tokenizeSingleChar(AntlrTokenType.Char)
            }
            '\'' -> if (charSet) {
                tokenizeSingleChar(AntlrTokenType.Char)
            } else {
                tokenizeSingleChar(AntlrTokenType.Quote).also { currentMode = AntlrMode.Default }
            }
            '\\' -> {
                if (!checkEnd(charIndex + 1)) {
                    val nextChar = getChar(charIndex + 1)
                    // Match escaping char and char itself
                    when (nextChar) {
                        'u' -> {
                            val startIndex = charIndex
                            charIndex += 2 // Skip '\u'
                            val endIndex = charIndex + 4
                            while (charIndex < endIndex && charIndex < text.length && text[charIndex] in hexDigits) {
                                charIndex++
                            }
                            val channel = if (charIndex == endIndex) AntlrTokenChannel.Default else AntlrTokenChannel.Error
                            createToken(AntlrTokenType.UnicodeEscapedChar, startIndex, charIndex - startIndex, channel)
                        }
                        '\r', '\n' -> tokenizeSingleChar(AntlrTokenType.EscapedChar, AntlrTokenChannel.Error)
                        else -> tokenizeDoubleChar(AntlrTokenType.EscapedChar)
                    }
                } else {
                    tokenizeSingleChar(AntlrTokenType.EscapedChar, AntlrTokenChannel.Error)
                }
            }
            // Multiline string literals are not supported
            '\r', '\n' -> tokenizeLineBreak().also { currentMode = AntlrMode.Default }
            else -> tokenizeSingleChar(AntlrTokenType.Char)
        }
    }

    private fun tokenizeSingleChar(
        tokenType: AntlrTokenType,
        tokenChannel: AntlrTokenChannel = AntlrTokenChannel.Default
    ) : AntlrToken {
        return createToken(tokenType, charIndex, 1, tokenChannel).also { charIndex++ }
    }

    private fun tokenizeDoubleChar(tokenType: AntlrTokenType) : AntlrToken {
        return createToken(tokenType, charIndex, 2).also { charIndex += 2 }
    }

    private fun tokenizeKeywordOrId(keyword: Keyword) : AntlrToken {
        return tokenizeSequence(AntlrTokenType.ParserId, idContinueChars, keyword = keyword)
    }

    private fun tokenizeSequence(
        tokenType: AntlrTokenType,
        continueChars: Set<Char>,
        channel: AntlrTokenChannel = AntlrTokenChannel.Default,
        keyword: Keyword? = null,
    ): AntlrToken {
        val startIndex = charIndex
        charIndex++
        while (charIndex < text.length && text[charIndex] in continueChars) {
            charIndex++
        }

        var recognizedType = checkKeyword(tokenType, startIndex, keyword)

        if (keyword?.tokenizeToLeftBrace == true && recognizedType == keyword.tokenType) {
            val keywordEndIndex = charIndex
            var currentIndex = keywordEndIndex
            while (getChar(currentIndex) in whitespaceChars) {
                currentIndex++ // skip whitespaces
            }

            recognizedType = if (checkChar(currentIndex,'{')) {
                currentIndex++ // skip '{'
                charIndex = currentIndex
                keyword.tokenType
            }
            else {
                AntlrTokenType.ParserId
            }
        }

        return createToken(recognizedType, startIndex, charIndex - startIndex, channel)
    }

    private fun checkKeyword(tokenType: AntlrTokenType, start: Int, keyword: Keyword?): AntlrTokenType {
        if (keyword == null) return tokenType

        val keywordValue = keyword.value
        if (keywordValue.length < charIndex - start) { // Only strict match is relevant
            return tokenType
        }
        val offset = charIndex - keywordValue.length
        for (i in keywordValue.indices) {
            if (!checkChar(offset + i, keywordValue[i])) {
                return tokenType
            }
        }

        return keyword.tokenType
    }

    private fun tokenizeLineBreak(): AntlrToken {
        val startIndex = charIndex
        processLineBreak()
        return createToken(AntlrTokenType.LineBreak, startIndex, charIndex - startIndex, AntlrTokenChannel.Hidden)
    }

    private fun tokenizeComment(): AntlrToken {
        val startIndex = charIndex
        charIndex++ // skip '/'
        var tokenType: AntlrTokenType = AntlrTokenType.Error
        if (charIndex < text.length) {
            if (text[charIndex] == '/') {
                charIndex++ // skip '/'
                while (charIndex < text.length) {
                    if (checkLineBreak(text[charIndex])) {
                        break
                    }
                    charIndex++
                }
                tokenType = AntlrTokenType.LineComment
            } else if (text[charIndex] == '*') {
                charIndex++ // skip '*'
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

    private fun tokenizeErrorChar(): AntlrToken {
        return createToken(AntlrTokenType.Error, charIndex, 1, AntlrTokenChannel.Error).also { charIndex++ }
    }

    private fun processLineBreak() {
        val c = text[charIndex]
        charIndex++
        if (c == '\r' && checkChar(charIndex, '\n')) {
            charIndex++
        }
        lineIndexes.add(charIndex)
    }

    private fun createToken(type: AntlrTokenType, offset: Int = -1, length: Int = -1, channel: AntlrTokenChannel = AntlrTokenChannel.Default): AntlrToken {
        return AntlrToken(type, offset, length, channel,
            value = if (initializeTokenValue) text.substring(offset, offset + length) else null
        ).also {
            if (diagnosticReporter != null && channel == AntlrTokenChannel.Error) {
                diagnosticReporter.invoke(UnrecognizedToken(it, it.getInterval()))
            }
        }
    }

    private fun checkEnd(index: Int = charIndex): Boolean = index >= text.length

    private fun checkLineBreak(value: Char) = value == '\r' || value == '\n'

    private fun checkChar(index: Int, value: Char): Boolean = index >= 0 && index < text.length && text[index] == value

    private fun getChar(index: Int): Char = if (index >= 0 && index < text.length) text[index] else 0.toChar()
}
