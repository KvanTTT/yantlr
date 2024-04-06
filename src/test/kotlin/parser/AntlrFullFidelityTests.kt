package parser

import infrastructure.AntlrFullFidelityDumper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

object AntlrFullFidelityTests {
    @Test
    fun fullFidelityTree() {
        check(ExampleData.GRAMMAR)
    }

    @Test
    fun treeWithErrors() {
        check("grammar + test")
    }

    private fun check(text: String) {
        val lexer = AntlrLexer(text)
        val tokenStream = AntlrLexerTokenStream(lexer)
        val parser = AntlrParser(tokenStream)
        val treeNode = parser.parseGrammar()

        val result = AntlrFullFidelityDumper(lexer, tokenStream.tokens).dump(treeNode)
        assertEquals(text, result)
    }
}