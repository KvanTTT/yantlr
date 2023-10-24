import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import AntlrTokenType.*

class AntlrParserTests {
    private fun token(tokenType: AntlrTokenType, value: String? = null): AntlrToken {
        return AntlrToken.createAbstractToken(tokenType, value = value)
    }

    private val defaultTreeNode = GrammarNode(
        null,
        token(Grammar),
        token(ParserId, "test"),
        token(Semicolon),

        listOf(RuleNode(
            RuleNode.AltParserIdNode(token(ParserId, "x")),
            token(Colon),

            BlockNode(
                AlternativeNode(listOf(ElementNode.ElementLexerId(token(LexerId, "A")))),

                listOf(
                    BlockNode.OrAlternativeNode(
                        token(Or),
                        AlternativeNode(listOf(ElementNode.ElementParserId(token(ParserId, "b"))))
                    ),
                    BlockNode.OrAlternativeNode(
                        token(Or),
                        AlternativeNode(
                            listOf(ElementNode.ElementBlock(
                                token(LeftParen),
                                BlockNode(
                                    AlternativeNode(listOf(ElementNode.ElementLexerId(token(LexerId, "C")))),
                                    listOf(BlockNode.OrAlternativeNode(
                                        token(Or),
                                        AlternativeNode(listOf(ElementNode.ElementParserId(token(ParserId, "d")))),
                                    ))
                                ),
                                token(RightParen)
                            ))
                        )
                    ),
                    BlockNode.OrAlternativeNode(
                        token(Or),
                        AlternativeNode(emptyList())
                    ),
                )
            ),

            token(Semicolon),
        ))
    )

    private val defaultTreeString = """
Grammar
  Token (Grammar)
  Token (ParserId, test)
  Token (Semicolon)
  Rule
    AltParserId
      Token (ParserId, x)
    Token (Colon)
    Block
      Alternative
        ElementLexerId
          Token (LexerId, A)
      OrAlternative
        Token (Or)
        Alternative
          ElementParserId
            Token (ParserId, b)
      OrAlternative
        Token (Or)
        Alternative
          ElementBlock
            Token (LeftParen)
            Block
              Alternative
                ElementLexerId
                  Token (LexerId, C)
              OrAlternative
                Token (Or)
                Alternative
                  ElementParserId
                    Token (ParserId, d)
            Token (RightParen)
      OrAlternative
        Token (Or)
        Alternative
    Token (Semicolon)

""".trimIndent()

    @Test
    fun testPrettifyAntlrNode() {
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
    fun testParser() {
        val tokens = listOf(
            AntlrToken.createAbstractToken(Grammar),
            AntlrToken.createAbstractToken(ParserId, value = "test"),
            AntlrToken.createAbstractToken(Semicolon),

            AntlrToken.createAbstractToken(ParserId, value = "x"),
            AntlrToken.createAbstractToken(Colon),
            AntlrToken.createAbstractToken(LexerId, value = "A"),
            AntlrToken.createAbstractToken(Or),
            AntlrToken.createAbstractToken(ParserId, value = "b"),
            AntlrToken.createAbstractToken(Or),
            AntlrToken.createAbstractToken(LeftParen),
            AntlrToken.createAbstractToken(LexerId, value = "C"),
            AntlrToken.createAbstractToken(Or),
            AntlrToken.createAbstractToken(ParserId, value = "d"),
            AntlrToken.createAbstractToken(RightParen),
            AntlrToken.createAbstractToken(Or),
            AntlrToken.createAbstractToken(Semicolon)
        )
        val tokenStream = AntlrListTokenStream(tokens)
        val parser = AntlrParser(tokenStream)
        val result = parser.parseGrammar()

        assertEquals(defaultTreeString, AntlrPrettier().prettify(result))
    }
}