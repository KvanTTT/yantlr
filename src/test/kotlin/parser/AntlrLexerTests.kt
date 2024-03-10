package parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AntlrLexerTests {
    @Test
    fun testEmpty() {
        checkTokens("", listOf())
    }

    @Test
    fun operators() {
        checkTokens(
            ", ; ( ) = : | * + += . .. } ? ~ -> #",
            listOf(
                AntlrTokenType.Comma, AntlrTokenType.Semicolon, AntlrTokenType.LeftParen, AntlrTokenType.RightParen,
                AntlrTokenType.Equals, AntlrTokenType.Colon,
                AntlrTokenType.Bar, AntlrTokenType.Star, AntlrTokenType.Plus, AntlrTokenType.PlusAssign,
                AntlrTokenType.Dot,
                AntlrTokenType.Range, AntlrTokenType.RightBrace, AntlrTokenType.Question, AntlrTokenType.Tilde,
                AntlrTokenType.RightArrow, AntlrTokenType.Pound
            ).map { AntlrToken(it) },
        )

        checkTokens(
            "- / &",
            listOf(
                AntlrToken(AntlrTokenType.Error, channel = AntlrTokenChannel.Error, value = "-"),
                AntlrToken(AntlrTokenType.Error, channel = AntlrTokenChannel.Error, value = "/"),
                AntlrToken(AntlrTokenType.Error, channel = AntlrTokenChannel.Error, value = "&"),
            ),
        )
    }

    @Test
    fun keywords() {
        checkTokens(
            "channels { fragment grammar import lexer mode options  { parser tokens {",
            listOf(
                AntlrTokenType.Channels,
                AntlrTokenType.Fragment, AntlrTokenType.Grammar, AntlrTokenType.Import, AntlrTokenType.Lexer,
                AntlrTokenType.Mode, AntlrTokenType.Options,
                AntlrTokenType.Parser, AntlrTokenType.Tokens
            ).map { AntlrToken(it) },
        )
    }

    @Test
    fun identifiers() {
        checkTokens(
            "TokenId parserId Привет привет grammarId options options1",
            listOf(
                AntlrTokenType.LexerId, AntlrTokenType.ParserId, AntlrTokenType.LexerId, AntlrTokenType.ParserId,
                AntlrTokenType.ParserId, AntlrTokenType.ParserId, AntlrTokenType.ParserId
            ).map { AntlrToken(it) },
        )
    }

    @Test
    fun strings() {
        checkTokens(
            """'a' '\'' '\\' '\x' '\u09AF'""",
            listOf(
                AntlrToken(AntlrTokenType.Quote, value = "'"),
                AntlrToken(AntlrTokenType.Char, value = "a"),
                AntlrToken(AntlrTokenType.Quote, value = "'"),

                AntlrToken(AntlrTokenType.Quote, value = "'"),
                AntlrToken(AntlrTokenType.EscapedChar, value = "\\'"),
                AntlrToken(AntlrTokenType.Quote, value = "'"),

                AntlrToken(AntlrTokenType.Quote, value = "'"),
                AntlrToken(AntlrTokenType.EscapedChar, value = "\\\\"),
                AntlrToken(AntlrTokenType.Quote, value = "'"),

                AntlrToken(AntlrTokenType.Quote, value = "'"),
                AntlrToken(AntlrTokenType.EscapedChar, value = "\\x"),
                AntlrToken(AntlrTokenType.Quote, value = "'"),

                AntlrToken(AntlrTokenType.Quote, value = "'"),
                AntlrToken(AntlrTokenType.UnicodeEscapedChar, value = "\\u09AF"),
                AntlrToken(AntlrTokenType.Quote, value = "'"),
            ),
        )
    }

    @Test
    fun incorrectStrings() {
        checkTokens(
            """
                '
                's
                '\
                '\u
                '\u0
                '\uxz
            """.trimIndent(),
            listOf(
                AntlrToken(AntlrTokenType.Quote),

                AntlrToken(AntlrTokenType.Quote),
                AntlrToken(AntlrTokenType.Char, value = "s"),

                AntlrToken(AntlrTokenType.Quote),
                AntlrToken(AntlrTokenType.EscapedChar, channel = AntlrTokenChannel.Error, value = "\\"),

                AntlrToken(AntlrTokenType.Quote),
                AntlrToken(AntlrTokenType.UnicodeEscapedChar, channel = AntlrTokenChannel.Error, value = "\\u"),

                AntlrToken(AntlrTokenType.Quote),
                AntlrToken(AntlrTokenType.UnicodeEscapedChar, channel = AntlrTokenChannel.Error, value = "\\u0"),

                AntlrToken(AntlrTokenType.Quote),
                AntlrToken(AntlrTokenType.UnicodeEscapedChar, channel = AntlrTokenChannel.Error, value = "\\u"),
                AntlrToken(AntlrTokenType.Char, value = "x"),
                AntlrToken(AntlrTokenType.Char, value = "z"),
            ),
        )
    }

    @Test
    fun comments() {
        checkTokens(
            "// Single line comment\n/* Multi line comment */",
            listOf(
                AntlrToken(AntlrTokenType.LineComment, channel = AntlrTokenChannel.Hidden, value = "// Single line comment"),
                AntlrToken(AntlrTokenType.BlockComment, channel = AntlrTokenChannel.Hidden, value = "/* Multi line comment */")
            ),
            ignoreWhitespaces = true,
        )

        // Check comments at the end of file
        checkTokens(
            "// Single line comment",
            listOf(AntlrToken(AntlrTokenType.LineComment, channel = AntlrTokenChannel.Hidden, value = "// Single line comment"))
        )
        checkTokens(
            "/* Multi line comment",
            listOf(AntlrToken(AntlrTokenType.BlockComment, channel = AntlrTokenChannel.Hidden, value = "/* Multi line comment"))
        )
    }

    @Test
    fun lexerCharSet() {
        checkTokens(
            """TOKEN options { caseInsensitive = true ; } : [a-z'"[\]\.];""",
            listOf(
                AntlrToken(AntlrTokenType.LexerId, value = "TOKEN"),
                AntlrToken(AntlrTokenType.Options),
                AntlrToken(AntlrTokenType.ParserId, value = "caseInsensitive"),
                AntlrToken(AntlrTokenType.Equals),
                AntlrToken(AntlrTokenType.ParserId, value = "true"),
                AntlrToken(AntlrTokenType.Semicolon),
                AntlrToken(AntlrTokenType.RightBrace),
                AntlrToken(AntlrTokenType.Colon),
                AntlrToken(AntlrTokenType.LeftBracket),
                AntlrToken(AntlrTokenType.Char, value = "a"),
                AntlrToken(AntlrTokenType.Hyphen),
                AntlrToken(AntlrTokenType.Char, value = "z"),
                AntlrToken(AntlrTokenType.Char, value = "'"),
                AntlrToken(AntlrTokenType.Char, value = "\""),
                AntlrToken(AntlrTokenType.Char, value = "["),
                AntlrToken(AntlrTokenType.EscapedChar, value = "\\]"),
                AntlrToken(AntlrTokenType.EscapedChar, value = "\\."),
                AntlrToken(AntlrTokenType.RightBracket),
                AntlrToken(AntlrTokenType.Semicolon),
            )
        )

        checkTokens(
            """
                TOKEN : [a
                TOKEN2 : [b\
                TOKEN3 : [c
            """.trimIndent(),
            listOf(
                AntlrToken(AntlrTokenType.LexerId, value = "TOKEN"),
                AntlrToken(AntlrTokenType.Colon),
                AntlrToken(AntlrTokenType.LeftBracket, value = "["),
                AntlrToken(AntlrTokenType.Char, value = "a"),
                AntlrToken(AntlrTokenType.LexerId, value = "TOKEN2"),
                AntlrToken(AntlrTokenType.Colon),
                AntlrToken(AntlrTokenType.LeftBracket, value = "["),
                AntlrToken(AntlrTokenType.Char, value = "b"),
                AntlrToken(AntlrTokenType.EscapedChar, channel = AntlrTokenChannel.Error, value = "\\"),
                AntlrToken(AntlrTokenType.LexerId, value = "TOKEN3"),
                AntlrToken(AntlrTokenType.Colon),
                AntlrToken(AntlrTokenType.LeftBracket),
                AntlrToken(AntlrTokenType.Char, value = "c"),
            )
        )
    }

    @Test
    fun bom() {
        checkTokens(
            "\uFEFF",
            listOf(
                AntlrToken(AntlrTokenType.Bom, channel = AntlrTokenChannel.Hidden, value = "\uFEFF")
            ),
            ignoreWhitespaces = true,
        )
    }

    @Test
    fun lineNumbers() {
        val text = "a\nb11\r\nc222\rd/*\n*/e\n\nf"
        val lexer = AntlrLexer(text)

        fun checkNextToken(expectedValue: String, expectedStartLine: Int, expectedStartColumn: Int, expectedEndLine: Int, expectedEndColumn: Int) {
            val actualToken = lexer.nextToken()
            assertEquals(expectedValue, lexer.getTokenValue(actualToken))
            val (startLine, startColumn) = lexer.getLineColumn(actualToken.offset)
            assertEquals(expectedStartLine, startLine)
            assertEquals(expectedStartColumn, startColumn)
            val (endLine, endColumn) = lexer.getLineColumn(actualToken.end())
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
        expectedTokens: List<AntlrToken>,
        ignoreWhitespaces: Boolean = true
    ) {
        val lexer = AntlrLexer(input)
        val tokenComparer = AntlrTreeComparer(lexer)

        for (expectedToken in expectedTokens) {
            var actualToken: AntlrToken
            do {
                actualToken = lexer.nextToken()
            } while (ignoreWhitespaces && (actualToken.type == AntlrTokenType.Whitespace || actualToken.type == AntlrTokenType.LineBreak))

            tokenComparer.compareToken(expectedToken, actualToken)
        }

        assertEquals(lexer.charIndex, input.length, "Lexer did not consume all input")
    }
}