import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import AntlrTokenType.*

class AntlrParserTests {
    companion object {
        val defaultTreeString = """
Grammar
  Token (Grammar, grammar)
  Token (ParserId, test)
  Token (Semicolon, ;)
  Rule
    AltParserId
      Token (ParserId, x)
    Token (Colon, :)
    Block
      Alternative
        ElementLexerId
          Token (LexerId, A)
      OrAlternative
        Token (Bar, |)
        Alternative
          ElementParserId
            Token (ParserId, b)
      OrAlternative
        Token (Bar, |)
        Alternative
          ElementBlock
            Token (LeftParen, ()
            Block
              Alternative
                ElementLexerId
                  Token (LexerId, C)
              OrAlternative
                Token (Bar, |)
                Alternative
                  ElementParserId
                    Token (ParserId, d)
            Token (RightParen, ))
      OrAlternative
        Token (Bar, |)
        Alternative
    Token (Semicolon, ;)

""".trimIndent()
    }

    private fun token(tokenType: AntlrTokenType, value: String): AntlrToken {
        return AntlrToken.createAbstractToken(tokenType, value = value)
    }

    private val defaultTreeNode = GrammarNode(
        null,
        token(Grammar, "grammar"),
        token(ParserId, "test"),
        token(Semicolon, ";"),

        listOf(RuleNode(
            RuleNode.AltParserIdNode(token(ParserId, "x")),
            token(Colon, ":"),

            BlockNode(
                AlternativeNode(listOf(ElementNode.ElementLexerId(token(LexerId, "A")))),

                listOf(
                    BlockNode.OrAlternativeNode(
                        token(Bar, "|"),
                        AlternativeNode(listOf(ElementNode.ElementParserId(token(ParserId, "b"))))
                    ),
                    BlockNode.OrAlternativeNode(
                        token(Bar, "|"),
                        AlternativeNode(
                            listOf(ElementNode.ElementBlock(
                                token(LeftParen, "("),
                                BlockNode(
                                    AlternativeNode(listOf(ElementNode.ElementLexerId(token(LexerId, "C")))),
                                    listOf(BlockNode.OrAlternativeNode(
                                        token(Bar, "|"),
                                        AlternativeNode(listOf(ElementNode.ElementParserId(token(ParserId, "d")))),
                                    ))
                                ),
                                token(RightParen, ")")
                            ))
                        )
                    ),
                    BlockNode.OrAlternativeNode(
                        token(Bar, "|"),
                        AlternativeNode(emptyList())
                    ),
                )
            ),

            token(Semicolon, ";"),
        ))
    )

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
            AntlrToken.createAbstractToken(Grammar, value = "grammar"),
            AntlrToken.createAbstractToken(ParserId, value = "test"),
            AntlrToken.createAbstractToken(Semicolon, value = ";"),

            AntlrToken.createAbstractToken(ParserId, value = "x"),
            AntlrToken.createAbstractToken(Colon, value = ":"),
            AntlrToken.createAbstractToken(LexerId, value = "A"),
            AntlrToken.createAbstractToken(Bar, value = "|"),
            AntlrToken.createAbstractToken(ParserId, value = "b"),
            AntlrToken.createAbstractToken(Bar, value = "|"),
            AntlrToken.createAbstractToken(LeftParen, value = "("),
            AntlrToken.createAbstractToken(LexerId, value = "C"),
            AntlrToken.createAbstractToken(Bar, value = "|"),
            AntlrToken.createAbstractToken(ParserId, value = "d"),
            AntlrToken.createAbstractToken(RightParen, value = ")"),
            AntlrToken.createAbstractToken(Bar, value = "|"),
            AntlrToken.createAbstractToken(Semicolon, value = ";")
        )
        val tokenStream = AntlrTokenStream(tokens)
        val parser = AntlrParser(tokenStream)
        val result = parser.parseGrammar()

        assertEquals(defaultTreeString, AntlrPrettier().prettify(result))
    }
}