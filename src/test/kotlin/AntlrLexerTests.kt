import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AntlrLexerTests {
    @Test
    fun testLexer() {
        val text = """
            'string' 'error
            LexerId parserId : ; | * + ( ) `
            lexer parser grammar
        """.trimIndent()
        val lexer = AntlrLexer(text)

        fun checkNextToken(expectedType: AntlrTokenType, expectedValue: String, expectedChannel: AntlrTokenChannel = AntlrTokenChannel.Default) {
            val actualToken = lexer.nextToken()
            assertEquals(expectedType, actualToken.type)
            assertEquals(expectedValue, actualToken.value)
            assertEquals(expectedChannel, actualToken.channel)
        }

        fun checkNextWhitespace() {
            checkNextToken(AntlrTokenType.Whitespace, " ", AntlrTokenChannel.Whitespace)
        }

        fun checkNextLineBreak() {
            checkNextToken(AntlrTokenType.LineBreak, "\n", AntlrTokenChannel.Whitespace)
        }

        checkNextToken(AntlrTokenType.String, "'string'")
        checkNextWhitespace()
        checkNextToken(AntlrTokenType.String, "'error", AntlrTokenChannel.Error)
        checkNextLineBreak()
        checkNextToken(AntlrTokenType.LexerId, "LexerId")
        checkNextWhitespace()
        checkNextToken(AntlrTokenType.ParserId, "parserId")
        checkNextWhitespace()
        checkNextToken(AntlrTokenType.Colon, ":")
        checkNextWhitespace()
        checkNextToken(AntlrTokenType.Semicolon, ";")
        checkNextWhitespace()
        checkNextToken(AntlrTokenType.Bar, "|")
        checkNextWhitespace()
        checkNextToken(AntlrTokenType.Star, "*")
        checkNextWhitespace()
        checkNextToken(AntlrTokenType.Plus, "+")
        checkNextWhitespace()
        checkNextToken(AntlrTokenType.LeftParen, "(")
        checkNextWhitespace()
        checkNextToken(AntlrTokenType.RightParen, ")")
        checkNextWhitespace()
        checkNextToken(AntlrTokenType.Error, "`", AntlrTokenChannel.Error)
        checkNextLineBreak()
        checkNextToken(AntlrTokenType.Lexer, "lexer")
        checkNextWhitespace()
        checkNextToken(AntlrTokenType.Parser, "parser")
        checkNextWhitespace()
        checkNextToken(AntlrTokenType.Grammar, "grammar")
        checkNextToken(AntlrTokenType.Eof, "")
    }

    @Test
    fun testLineNumbers() {
        val text = "a\nb11\r\nc222\rd"
        val lexer = AntlrLexer(text)

        fun checkNextToken(expectedValue: String, expectedStartLine: Int, expectedStartColumn: Int, expectedEndLine: Int, expectedEndColumn: Int) {
            val actualToken = lexer.nextToken()
            assertEquals(expectedValue, actualToken.value)
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
    }
}