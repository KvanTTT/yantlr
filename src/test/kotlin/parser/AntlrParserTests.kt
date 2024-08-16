package parser

import infrastructure.AntlrTreePrettier
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import parser.AntlrTokenType.*

object AntlrParserTests {
    @Test
    fun prettifyAntlrNode() {
        assertEquals(ExampleData.TREE_STRING, AntlrTreePrettier().prettify(ExampleData.TreeNode))
    }

    /**
     * grammar test;
     *
     * x
     *     : A
     *     | b
     *     | (C | d)
     *     |
     *     ;
     */
    @Test
    fun parser() {
        val tokens = listOf(
            AntlrToken(Grammar),
            AntlrToken(ParserId, value = "test"),
            AntlrToken(Semicolon),

            AntlrToken(ParserId, value = "x"),
            AntlrToken(Colon),
            AntlrToken(LexerId, value = "A"),
            AntlrToken(Bar),
            AntlrToken(ParserId, value = "b"),
            AntlrToken(Bar),
            AntlrToken(LeftParen),
            AntlrToken(LexerId, value = "C"),
            AntlrToken(Bar),
            AntlrToken(ParserId, value = "d"),
            AntlrToken(RightParen),
            AntlrToken(Bar),
            AntlrToken(Semicolon),
            AntlrToken(Eof),
        )
        val tokenStream = AntlrListTokenStream(tokens)
        val parser = AntlrParser(tokenStream)
        val result = parser.parseGrammar()

        assertEquals(ExampleData.TREE_STRING, AntlrTreePrettier().prettify(result))
    }
}