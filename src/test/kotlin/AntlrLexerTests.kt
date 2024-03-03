import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AntlrLexerTests {
    data class ExpectedToken(
        val type: AntlrTokenType,
        val channel: AntlrTokenChannel = AntlrTokenChannel.Default,
        val value: String? = null
    )

    @Test
    fun testEmpty() {
        checkTokens("", listOf())
    }

    @Test
    fun testOperators() {
        checkTokens(
            ", ; ( ) = : | * + += . .. } ? ~ -> #",
            listOf(
                AntlrTokenType.Comma, AntlrTokenType.Semicolon, AntlrTokenType.LeftParen, AntlrTokenType.RightParen,
                AntlrTokenType.Equals, AntlrTokenType.Colon,
                AntlrTokenType.Bar, AntlrTokenType.Star, AntlrTokenType.Plus, AntlrTokenType.PlusAssign,
                AntlrTokenType.Dot,
                AntlrTokenType.Range, AntlrTokenType.RightBrace, AntlrTokenType.Question, AntlrTokenType.Tilde,
                AntlrTokenType.RightArrow, AntlrTokenType.Pound
            ).map { ExpectedToken(it) },
        )

        checkTokens(
            "- / &",
            listOf(
                ExpectedToken(AntlrTokenType.Error, AntlrTokenChannel.Error, "-"),
                ExpectedToken(AntlrTokenType.Error, AntlrTokenChannel.Error, "/"),
                ExpectedToken(AntlrTokenType.Error, AntlrTokenChannel.Error, "&"),
            ),
        )
    }

    @Test
    fun testKeywords() {
        checkTokens(
            "channels { fragment grammar import lexer mode options  { parser tokens {",
            listOf(
                AntlrTokenType.Channels,
                AntlrTokenType.Fragment, AntlrTokenType.Grammar, AntlrTokenType.Import, AntlrTokenType.Lexer,
                AntlrTokenType.Mode, AntlrTokenType.Options,
                AntlrTokenType.Parser, AntlrTokenType.Tokens
            ).map { ExpectedToken(it) },
        )
    }

    @Test
    fun testIdentifiers() {
        checkTokens(
            "TokenId parserId Привет привет grammarId options options1",
            listOf(
                AntlrTokenType.LexerId, AntlrTokenType.ParserId, AntlrTokenType.LexerId, AntlrTokenType.ParserId,
                AntlrTokenType.ParserId, AntlrTokenType.ParserId, AntlrTokenType.ParserId
            ).map { ExpectedToken(it) },
        )
    }

    @Test
    fun testStrings() {
        checkTokens(
            """'a' '\'' '\\' '\x' '\u0000'""",
            listOf(
                ExpectedToken(AntlrTokenType.Quote, value = "'"),
                ExpectedToken(AntlrTokenType.Char, value = "a"),
                ExpectedToken(AntlrTokenType.Quote, value = "'"),

                ExpectedToken(AntlrTokenType.Quote, value = "'"),
                ExpectedToken(AntlrTokenType.EscapedChar, value = "\\'"),
                ExpectedToken(AntlrTokenType.Quote, value = "'"),

                ExpectedToken(AntlrTokenType.Quote, value = "'"),
                ExpectedToken(AntlrTokenType.EscapedChar, value = "\\\\"),
                ExpectedToken(AntlrTokenType.Quote, value = "'"),

                ExpectedToken(AntlrTokenType.Quote, value = "'"),
                ExpectedToken(AntlrTokenType.EscapedChar, value = "\\x"),
                ExpectedToken(AntlrTokenType.Quote, value = "'"),

                ExpectedToken(AntlrTokenType.Quote, value = "'"),
                ExpectedToken(AntlrTokenType.UnicodeEscapedChar, value = "\\u0000"),
                ExpectedToken(AntlrTokenType.Quote, value = "'"),
            ),
        )
    }

    @Test
    fun testIncorrectStrings() {
        checkTokens(
            """
                '
                's
                '\
                '\u
                '\u0
                '\uab
            """.trimIndent(),
            listOf(
                ExpectedToken(AntlrTokenType.Quote),

                ExpectedToken(AntlrTokenType.Quote),
                ExpectedToken(AntlrTokenType.Char, value = "s"),

                ExpectedToken(AntlrTokenType.Quote),
                ExpectedToken(AntlrTokenType.EscapedChar, AntlrTokenChannel.Error, value = "\\"),

                ExpectedToken(AntlrTokenType.Quote),
                ExpectedToken(AntlrTokenType.UnicodeEscapedChar, AntlrTokenChannel.Error, value = "\\u"),

                ExpectedToken(AntlrTokenType.Quote),
                ExpectedToken(AntlrTokenType.UnicodeEscapedChar, AntlrTokenChannel.Error, value = "\\u0"),

                ExpectedToken(AntlrTokenType.Quote),
                ExpectedToken(AntlrTokenType.UnicodeEscapedChar, AntlrTokenChannel.Error, value = "\\u"),
                ExpectedToken(AntlrTokenType.Char, value = "a"),
                ExpectedToken(AntlrTokenType.Char, value = "b"),
            ),
        )
    }

    @Test
    fun testComments() {
        checkTokens(
            "// Single line comment\n/* Multi line comment */",
            listOf(
                ExpectedToken(AntlrTokenType.LineComment, AntlrTokenChannel.Hidden, value = "// Single line comment"),
                ExpectedToken(AntlrTokenType.BlockComment, AntlrTokenChannel.Hidden, value = "/* Multi line comment */")
            ),
            ignoreWhitespaces = true,
        )

        // Check comments at the end of file
        checkTokens(
            "// Single line comment",
            listOf(ExpectedToken(AntlrTokenType.LineComment, AntlrTokenChannel.Hidden, value = "// Single line comment"))
        )
        checkTokens(
            "/* Multi line comment",
            listOf(ExpectedToken(AntlrTokenType.BlockComment, AntlrTokenChannel.Hidden, value = "/* Multi line comment"))
        )
    }

    @Test
    fun testLexerCharSet() {
        checkTokens(
            """TOKEN options { caseInsensitive = true ; } : [a-z'"[\]\.];""",
            listOf(
                ExpectedToken(AntlrTokenType.LexerId, value = "TOKEN"),
                ExpectedToken(AntlrTokenType.Options),
                ExpectedToken(AntlrTokenType.ParserId, value = "caseInsensitive"),
                ExpectedToken(AntlrTokenType.Equals),
                ExpectedToken(AntlrTokenType.ParserId, value = "true"),
                ExpectedToken(AntlrTokenType.Semicolon),
                ExpectedToken(AntlrTokenType.RightBrace),
                ExpectedToken(AntlrTokenType.Colon),
                ExpectedToken(AntlrTokenType.LeftBracket),
                ExpectedToken(AntlrTokenType.Char, value = "a"),
                ExpectedToken(AntlrTokenType.Hyphen),
                ExpectedToken(AntlrTokenType.Char, value = "z"),
                ExpectedToken(AntlrTokenType.Char, value = "'"),
                ExpectedToken(AntlrTokenType.Char, value = "\""),
                ExpectedToken(AntlrTokenType.Char, value = "["),
                ExpectedToken(AntlrTokenType.EscapedChar, value = "\\]"),
                ExpectedToken(AntlrTokenType.EscapedChar, value = "\\."),
                ExpectedToken(AntlrTokenType.RightBracket),
                ExpectedToken(AntlrTokenType.Semicolon),
            )
        )

        checkTokens(
            """
                TOKEN : [a
                TOKEN2 : [b\
                TOKEN3 : [c
            """.trimIndent(),
            listOf(
                ExpectedToken(AntlrTokenType.LexerId, value = "TOKEN"),
                ExpectedToken(AntlrTokenType.Colon),
                ExpectedToken(AntlrTokenType.LeftBracket, value = "["),
                ExpectedToken(AntlrTokenType.Char, value = "a"),
                ExpectedToken(AntlrTokenType.LexerId, value = "TOKEN2"),
                ExpectedToken(AntlrTokenType.Colon),
                ExpectedToken(AntlrTokenType.LeftBracket, value = "["),
                ExpectedToken(AntlrTokenType.Char, value = "b"),
                ExpectedToken(AntlrTokenType.EscapedChar, AntlrTokenChannel.Error, value = "\\"),
                ExpectedToken(AntlrTokenType.LexerId, value = "TOKEN3"),
                ExpectedToken(AntlrTokenType.Colon),
                ExpectedToken(AntlrTokenType.LeftBracket),
                ExpectedToken(AntlrTokenType.Char, value = "c"),
            )
        )
    }

    @Test
    fun testBom() {
        checkTokens(
            "\uFEFF",
            listOf(
                ExpectedToken(AntlrTokenType.Bom, AntlrTokenChannel.Hidden, value = "\uFEFF")
            ),
            ignoreWhitespaces = true,
        )
    }

    @Test
    fun testLineNumbers() {
        val text = "a\nb11\r\nc222\rd/*\n*/e\n\nf"
        val lexer = AntlrLexer(text)

        fun checkNextToken(expectedValue: String, expectedStartLine: Int, expectedStartColumn: Int, expectedEndLine: Int, expectedEndColumn: Int) {
            val actualToken = lexer.nextToken()
            assertEquals(expectedValue, lexer.getTokenValue(actualToken))
            val (startLine, startColumn) = lexer.lineColumn(actualToken.index)
            assertEquals(expectedStartLine, startLine)
            assertEquals(expectedStartColumn, startColumn)
            val (endLine, endColumn) = lexer.lineColumn(actualToken.end())
            assertEquals(expectedEndLine, endLine)
            assertEquals(expectedEndColumn, endColumn)
        }

        checkNextToken("a", 1, 1, 1, 2)
        checkNextToken("\n", 1, 2, 2, 1)
        checkNextToken("b11", 2, 1, 2, 4)
        checkNextToken("\r\n", 2, 4, 3, 1)
        checkNextToken("c222", 3, 1, 3, 5)
        checkNextToken("\r", 3, 5, 4, 1)
        checkNextToken("d", 4, 1, 4, 2)
        checkNextToken("/*\n*/", 4, 2, 5, 3)
        checkNextToken("e", 5, 3, 5, 4)
        checkNextToken("\n", 5, 4, 6, 1)
        checkNextToken("\n", 6, 1, 7, 1)
        checkNextToken("f", 7, 1, 7, 2)
    }

    private fun checkTokens(
        input: String,
        expectedTokens: List<ExpectedToken>,
        ignoreWhitespaces: Boolean = true
    ) {
        val lexer = AntlrLexer(input)

        for (expectedToken in expectedTokens) {
            var token: AntlrToken
            do {
                token = lexer.nextToken()
            } while (ignoreWhitespaces && (token.type == AntlrTokenType.Whitespace || token.type == AntlrTokenType.LineBreak))

            assertEquals(expectedToken.type, token.type, "Expected token type: ${expectedToken.type.name}")
            assertEquals(expectedToken.channel, token.channel, "Expected token channel: ${expectedToken.channel.name}")
            if (expectedToken.value != null) {
                assertEquals(expectedToken.value, lexer.getTokenValue(token))
            }
        }

        assertEquals(lexer.charIndex, input.length, "Lexer did not consume all input")
    }
}