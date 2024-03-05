package semantics

import parser.AntlrLexer
import parser.AntlrLexerTokenStream
import parser.AntlrParser
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleCollectorTests {
    @Test
    fun testRules() {
        val grammar = """
            grammar test;
            x: A;
            y: B;
            z: C;
        """.trimIndent()

        val lexer = AntlrLexer(grammar)
        val tokenStream = AntlrLexerTokenStream(lexer)
        val parser = AntlrParser(tokenStream)
        val result = parser.parseGrammar()

        val collector = RuleCollector(lexer)
        val rules = collector.collect(result)
        assertEquals(3, rules.size)

        rules.onEachIndexed { index, entry ->
            val expectedRule = when (index) {
                0 -> "x"
                1 -> "y"
                2 -> "z"
                else -> throw IllegalStateException("Unexpected index: $index")
            }
            assertEquals(expectedRule, entry.key)
        }
    }
}