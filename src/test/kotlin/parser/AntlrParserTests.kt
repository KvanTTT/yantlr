package parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import parser.AntlrTokenType.*

class AntlrParserTests {
    companion object {
        val defaultTreeString = """
Grammar
  Token (Grammar, grammar)
  Token (ParserId, test)
  Token (Semicolon, ;)
  Rule
    Token (ParserId, x)
    Token (Colon, :)
    Block
      Alternative
        LexerId
          Token (LexerId, A)
      OrAlternative
        Token (Bar, |)
        Alternative
          ParserId
            Token (ParserId, b)
      OrAlternative
        Token (Bar, |)
        Alternative
          Block
            Token (LeftParen, ()
            Block
              Alternative
                LexerId
                  Token (LexerId, C)
              OrAlternative
                Token (Bar, |)
                Alternative
                  ParserId
                    Token (ParserId, d)
            Token (RightParen, ))
      OrAlternative
        Token (Bar, |)
        Alternative
          Empty
    Token (Semicolon, ;)
  Token (Eof)

""".trimIndent()

        val defaultTreeNode = GrammarNode(
            null,
            AntlrToken(Grammar, value = "grammar"),
            AntlrToken(ParserId, value = "test"),
            AntlrToken(Semicolon, value = ";"),

            listOf(
                RuleNode(
                    AntlrToken(ParserId, value = "x"),
                    AntlrToken(Colon, value = ":"),

                    BlockNode(
                        AlternativeNode(listOf(ElementNode.LexerId(AntlrToken(LexerId, value = "A")))),

                        listOf(
                            BlockNode.OrAlternativeNode(
                                AntlrToken(Bar, value = "|"),
                                AlternativeNode(listOf(ElementNode.ParserId(AntlrToken(ParserId, value = "b"))))
                            ),
                            BlockNode.OrAlternativeNode(
                                AntlrToken(Bar, value = "|"),
                                AlternativeNode(
                                    listOf(
                                        ElementNode.Block(
                                            AntlrToken(LeftParen, value = "("),
                                            BlockNode(
                                                AlternativeNode(listOf(ElementNode.LexerId(AntlrToken(LexerId, value = "C")))),
                                                listOf(
                                                    BlockNode.OrAlternativeNode(
                                                        AntlrToken(Bar, value = "|"),
                                                        AlternativeNode(listOf(ElementNode.ParserId(AntlrToken(ParserId, value = "d")))),
                                                    ))
                                            ),
                                            AntlrToken(RightParen, value = ")")
                                        ))
                                )
                            ),
                            BlockNode.OrAlternativeNode(
                                AntlrToken(Bar, value = "|"),
                                AlternativeNode(
                                    listOf(ElementNode.Empty())
                                )
                            ),
                        )
                    ),

                    AntlrToken(Semicolon, value = ";"),
                )
            ),

            AntlrToken(Eof)
        )
    }

    @Test
    fun prettifyAntlrNode() {
        assertEquals(defaultTreeString, AntlrPrettier().prettify(defaultTreeNode))
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
            AntlrToken(Grammar, value = "grammar"),
            AntlrToken(ParserId, value = "test"),
            AntlrToken(Semicolon, value = ";"),

            AntlrToken(ParserId, value = "x"),
            AntlrToken(Colon, value = ":"),
            AntlrToken(LexerId, value = "A"),
            AntlrToken(Bar, value = "|"),
            AntlrToken(ParserId, value = "b"),
            AntlrToken(Bar, value = "|"),
            AntlrToken(LeftParen, value = "("),
            AntlrToken(LexerId, value = "C"),
            AntlrToken(Bar, value = "|"),
            AntlrToken(ParserId, value = "d"),
            AntlrToken(RightParen, value = ")"),
            AntlrToken(Bar, value = "|"),
            AntlrToken(Semicolon, value = ";")
        )
        val tokenStream = AntlrListTokenStream(tokens)
        val parser = AntlrParser(tokenStream)
        val result = parser.parseGrammar()

        assertEquals(defaultTreeString, AntlrPrettier().prettify(result))
    }
}