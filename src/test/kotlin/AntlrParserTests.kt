import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import AntlrTokenType.*
import TreeNodeType.*

class AntlrParserTests {
    private fun TreeNodeType.tree(vararg children: AntlrTreeNode): AntlrTreeNode {
        return createNode(this, children.toList(), null)
    }

    private fun token(tokenType: AntlrTokenType, value: String? = null): TokenTreeNode {
        return TokenTreeNode(AntlrToken.createAbstractToken(tokenType, value = value), null)
    }

    private val defaultTreeNode = GrammarRule.tree(
        token(Grammar),
        token(ParserId, "test"),
        token(Semicolon),

        RuleRule.tree(
            token(ParserId, "x"),
            token(Colon),

            BlockRule.tree(
                AlternativeRule.tree(
                    ElementRule.tree(token(LexerId, "A"))
                ),

                BlockRuleOrAlternative.tree(
                    token(Or),
                    AlternativeRule.tree(
                        ElementRule.tree(
                            token(ParserId, "b")
                        ),
                    )
                ),

                BlockRuleOrAlternative.tree(
                    token(Or),
                    AlternativeRule.tree(
                        ElementRule.tree(
                            token(LeftParen),
                            BlockRule.tree(
                                AlternativeRule.tree(
                                    ElementRule.tree(
                                        token(LexerId, "C")
                                    ),
                                ),
                                BlockRuleOrAlternative.tree(
                                    token(Or),
                                    AlternativeRule.tree(
                                        ElementRule.tree(
                                            token(ParserId, "d")
                                        ),
                                    )
                                )
                            ),
                            token(RightParen)
                        ),
                    ),
                ),

                BlockRuleOrAlternative.tree(
                    token(Or),
                ),
            ),

            token(Semicolon),
        )
    )

    private val defaultTreeString = """
                Grammar
                  Token (Grammar)
                  Token (ParserId, test)
                  Token (Semicolon)
                  Rule
                    Token (ParserId, x)
                    Token (Colon)
                    Block
                      Alternative
                        Element
                          Token (LexerId, A)
                      BlockRuleOrAlternative
                        Token (Or)
                        Alternative
                          Element
                            Token (ParserId, b)
                      BlockRuleOrAlternative
                        Token (Or)
                        Alternative
                          Element
                            Token (LeftParen)
                            Block
                              Alternative
                                Element
                                  Token (LexerId, C)
                              BlockRuleOrAlternative
                                Token (Or)
                                Alternative
                                  Element
                                    Token (ParserId, d)
                            Token (RightParen)
                      BlockRuleOrAlternative
                        Token (Or)
                    Token (Semicolon)

            """.trimIndent()

    @Test
    fun testPrettifyAntlrNode() {
        assertEquals(defaultTreeString, defaultTreeNode.toString())
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
        val result = parser.parse()

        assertEquals(defaultTreeString, result.toString())
    }
}