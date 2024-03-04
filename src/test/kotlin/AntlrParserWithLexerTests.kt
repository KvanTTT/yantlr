import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AntlrParserWithLexerTests {
    companion object {
        val defaultGrammar = """
// Begin comment
grammar test;
x
    : A
    | b
    | (C | d)
    |
    ;
// End comment
""".trimIndent()
    }

    @Test
    fun testParserWithLexer() {
        val lexer = AntlrLexer(defaultGrammar)
        val tokenStream = AntlrLexerTokenStream(lexer)
        val parser = AntlrParser(tokenStream)
        val result = parser.parseGrammar()

        assertEquals(AntlrParserTests.defaultTreeString, AntlrPrettier(lexer).prettify(result))
    }
}