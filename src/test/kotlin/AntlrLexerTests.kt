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
}