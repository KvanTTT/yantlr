package parser

object ExampleData {
    const val GRAMMAR =
"""// Begin comment
grammar test;
x
    : A
    | b
    | (C | d)
    |
    ;
// End comment
"""

    const val TREE_STRING =
"""Grammar
  Token (Grammar)
  Token (ParserId, test)
  Token (Semicolon)
  Mode
    Rule
      Token (ParserId, x)
      Token (Colon)
      Block
        Alternative
          Element
            LexerId
              Token (LexerId, A)
        OrAlternative
          Token (Bar)
          Alternative
            Element
              ParserId
                Token (ParserId, b)
        OrAlternative
          Token (Bar)
          Alternative
            Element
              Block
                Token (LeftParen)
                Block
                  Alternative
                    Element
                      LexerId
                        Token (LexerId, C)
                  OrAlternative
                    Token (Bar)
                    Alternative
                      Element
                        ParserId
                          Token (ParserId, d)
                Token (RightParen)
        OrAlternative
          Token (Bar)
          Alternative
            Element
              Empty
                Token (Empty)
      Token (Semicolon)
  End
    Token (Eof)
"""

    val TreeNode = GrammarNode(
        null,
        AntlrToken(AntlrTokenType.Grammar),
        AntlrToken(AntlrTokenType.ParserId, value = "test"),
        AntlrToken(AntlrTokenType.Semicolon),

        listOf(
            ModeNode(
                modeDeclaration = null,
                ruleNodes = listOf(
                    RuleNode(
                        fragmentToken = null,
                        AntlrToken(AntlrTokenType.ParserId, value = "x"),
                        AntlrToken(AntlrTokenType.Colon),

                        BlockNode(
                            AlternativeNode(
                                listOf(
                                    ElementNode(
                                        elementPrefix = null,
                                        ElementBody.LexerId(
                                            tilde = null,
                                            AntlrToken(AntlrTokenType.LexerId, value = "A"),
                                        ),
                                        elementSuffix = null
                                    )
                                )
                            ),

                            listOf(
                                BlockNode.OrAlternative(
                                    AntlrToken(AntlrTokenType.Bar),
                                    AlternativeNode(
                                        listOf(
                                            ElementNode(
                                                elementPrefix = null,
                                                ElementBody.ParserId(
                                                    tilde = null,
                                                    AntlrToken(AntlrTokenType.ParserId, value = "b"),
                                                ),
                                                elementSuffix = null
                                            )
                                        )
                                    ),
                                ),
                                BlockNode.OrAlternative(
                                    AntlrToken(AntlrTokenType.Bar),
                                    AlternativeNode(
                                        listOf(
                                            ElementNode(
                                                elementPrefix = null,
                                                ElementBody.Block(
                                                    tilde = null,
                                                    AntlrToken(AntlrTokenType.LeftParen),
                                                    BlockNode(
                                                        AlternativeNode(
                                                            listOf(
                                                                ElementNode(
                                                                    elementPrefix = null,
                                                                    ElementBody.LexerId(
                                                                        tilde = null,
                                                                        AntlrToken(AntlrTokenType.LexerId, value = "C"),
                                                                    ),
                                                                    elementSuffix = null
                                                                )
                                                            )
                                                        ),
                                                        listOf(
                                                            BlockNode.OrAlternative(
                                                                AntlrToken(AntlrTokenType.Bar),
                                                                AlternativeNode(
                                                                    listOf(
                                                                        ElementNode(
                                                                            elementPrefix = null,
                                                                            ElementBody.ParserId(
                                                                                tilde = null,
                                                                                AntlrToken(AntlrTokenType.ParserId, value = "d"),
                                                                            ),
                                                                            elementSuffix = null
                                                                        )
                                                                    )
                                                                ),
                                                            )
                                                        ),
                                                    ),
                                                    AntlrToken(AntlrTokenType.RightParen),
                                                ),
                                                elementSuffix = null
                                            )
                                        ),
                                    ),
                                ),
                                BlockNode.OrAlternative(
                                    AntlrToken(AntlrTokenType.Bar),
                                    AlternativeNode(
                                        listOf(
                                            ElementNode(
                                                elementPrefix = null,
                                                ElementBody.Empty(
                                                    tilde = null,
                                                    emptyToken = AntlrToken(AntlrTokenType.Empty),
                                                ),
                                                elementSuffix = null
                                            )
                                        ),
                                    ),
                                ),
                            ),
                        ),

                        commandsNode = null,

                        AntlrToken(AntlrTokenType.Semicolon),
                    ),
                )
            )
        ),

        EndNode(emptyList(), AntlrToken(AntlrTokenType.Eof)),
    )
}