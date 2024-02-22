import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AntlrParserWithLexerTests {
    @Test
    fun testParserWithLexer() {
        val testGrammar = """
grammar test;
x
    : A
    | b
    | (C | d)
    |
    ;
""".trimIndent()

        val lexer = AntlrLexer(testGrammar)
        val tokenStream = AntlrLexerTokenStream(lexer)
        val parser = AntlrParser(tokenStream)
        val result = parser.parseGrammar()

        assertEquals(AntlrParserTests.defaultTreeString, AntlrPrettier(lexer).prettify(result))
    }
}