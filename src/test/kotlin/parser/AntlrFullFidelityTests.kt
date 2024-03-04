package parser

import parser.AntlrParserWithLexerTests.Companion.defaultGrammar
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AntlrFullFidelityTests {
    @Test
    fun testFullFidelityTree() {
        val lexer = AntlrLexer(defaultGrammar)
        val tokenStream = AntlrLexerTokenStream(lexer)
        val parser = AntlrParser(tokenStream)
        val treeNode = parser.parseGrammar()
        val result = AntlrFullFidelityDumper(lexer, tokenStream.tokens).dump(treeNode)

        assertEquals(defaultGrammar, result)
    }
}