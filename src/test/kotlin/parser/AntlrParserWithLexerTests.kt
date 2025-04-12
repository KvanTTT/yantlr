package parser

import kotlin.test.Test

object AntlrParserWithLexerTests {
    @Test
    fun parserWithLexer() {
        infrastructure.check(ExampleData.TreeNode, ExampleData.GRAMMAR) { it.parseGrammar() }
    }

    @Test
    fun stringLiteral() {
        infrastructure.check(
            ElementNode(
                elementPrefix = null,
                ElementBody.StringLiteralOrRange(
                    tilde = null,
                    ElementBody.StringLiteralOrRange.StringLiteral(
                        AntlrToken(AntlrTokenType.Quote),
                        listOf(
                            AntlrToken(AntlrTokenType.Char, value = "a"),
                            AntlrToken(AntlrTokenType.Char, value = "b"),
                            AntlrToken(AntlrTokenType.EscapedChar, value = "\\r"),
                            AntlrToken(AntlrTokenType.EscapedChar, value = "\\t"),
                            AntlrToken(AntlrTokenType.UnicodeEscapedChar, value = "\\u000A"),
                        ),
                        AntlrToken(AntlrTokenType.Quote)
                    ),
                    range = null,
                ),
                elementSuffix = null,
            ),
            """'ab\r\t\u000A'"""
        ) { it.parseElement() }
    }

    @Test
    fun range() {
        infrastructure.check(
            ElementNode(
                elementPrefix = null,
                ElementBody.StringLiteralOrRange(
                    tilde = null,
                    ElementBody.StringLiteralOrRange.StringLiteral(
                        AntlrToken(AntlrTokenType.Quote),
                        listOf(AntlrToken(AntlrTokenType.Char, value = "a")),
                        AntlrToken(AntlrTokenType.Quote)
                    ),
                    range = ElementBody.StringLiteralOrRange.Range(
                        AntlrToken(AntlrTokenType.Range),
                        ElementBody.StringLiteralOrRange.StringLiteral(
                            AntlrToken(AntlrTokenType.Quote),
                            listOf(AntlrToken(AntlrTokenType.Char, value = "z")),
                            AntlrToken(AntlrTokenType.Quote)
                        )
                    ),
                ),
                elementSuffix = null
            ),
            """'a'..'z'"""
        ) { it.parseElement() }
    }

    @Test
    fun unterminatedStringLiteral() {
        infrastructure.check(
            ElementNode(
                elementPrefix = null,
                ElementBody.StringLiteralOrRange(
                    tilde = null,
                    ElementBody.StringLiteralOrRange.StringLiteral(
                        AntlrToken(AntlrTokenType.Quote),
                        listOf(AntlrToken(AntlrTokenType.Char, value = "a")),
                        AntlrToken(AntlrTokenType.Quote, channel = AntlrTokenChannel.Error),
                    ),
                    range = null,
                ),
                elementSuffix = null,
            ),
            """'a"""
        ) { it.parseElement() }
    }

    @Test
    fun stringLiteralWithIncorrectEscaping() {
        infrastructure.check(
            ElementNode(
                elementPrefix = null,
                ElementBody.StringLiteralOrRange(
                    tilde = null,
                    ElementBody.StringLiteralOrRange.StringLiteral(
                        AntlrToken(AntlrTokenType.Quote),
                        // Unrecognized token '\u' can be extract from hidden channel
                        listOf(AntlrToken(AntlrTokenType.Char, value = "X")),
                        AntlrToken(AntlrTokenType.Quote),
                    ),
                    range = null,
                ),
                elementSuffix = null,
            ),
            """'\uX'"""
        ) { it.parseElement() }
    }

    @Test
    fun charSet() {
        infrastructure.check(
            ElementNode(
                elementPrefix = null,
                ElementBody.CharSet(
                    tilde = null,
                    AntlrToken(AntlrTokenType.LeftBracket),
                    listOf(
                        ElementBody.CharSet.CharOrRange(
                            AntlrToken(AntlrTokenType.Hyphen, value = "-"),
                            range = null
                        ),
                        ElementBody.CharSet.CharOrRange(
                            AntlrToken(AntlrTokenType.Char, value = "a"),
                            ElementBody.CharSet.CharOrRange.Range(
                                AntlrToken(AntlrTokenType.Hyphen),
                                AntlrToken(AntlrTokenType.Char, value = "z")
                            )
                        ),
                        ElementBody.CharSet.CharOrRange(
                            AntlrToken(AntlrTokenType.Char, value = "0"),
                            ElementBody.CharSet.CharOrRange.Range(
                                AntlrToken(AntlrTokenType.Hyphen),
                                AntlrToken(AntlrTokenType.Char, value = "9")
                            )
                        ),
                        ElementBody.CharSet.CharOrRange(
                            AntlrToken(AntlrTokenType.Char, value = "Z"),
                            range = null
                        ),
                        ElementBody.CharSet.CharOrRange(
                            AntlrToken(AntlrTokenType.Hyphen, value = "-"),
                            range = null
                        ),
                    ),
                    AntlrToken(AntlrTokenType.RightBracket),
                ),
                elementSuffix = null,
            ),
            """[-a-z0-9Z-]"""
        ) { it.parseElement() }
    }

    @Test
    fun unterminatedCharSet() {
        infrastructure.check(
            ElementNode(
                elementPrefix = null,
                ElementBody.CharSet(
                    tilde = null,
                    AntlrToken(AntlrTokenType.LeftBracket),
                    listOf(
                        ElementBody.CharSet.CharOrRange(
                            AntlrToken(AntlrTokenType.Char, value = "a"),
                            range = null
                        ),
                        ElementBody.CharSet.CharOrRange(
                            AntlrToken(AntlrTokenType.Hyphen, value = "-"),
                            range = null
                        ),
                    ),
                    AntlrToken(AntlrTokenType.RightBracket, channel = AntlrTokenChannel.Error),
                ),
                elementSuffix = null,
            ),
            "[a-\n"
        ) { it.parseElement() }
    }

    @Test
    fun elementOptional() {
        infrastructure.check(
            ElementNode(
                elementPrefix = null,
                ElementBody.LexerId(
                    tilde = null,
                    AntlrToken(AntlrTokenType.LexerId, value = "A"),
                ),
                elementSuffix = ElementSuffixNode(
                    AntlrToken(AntlrTokenType.Question),
                    nonGreedy = null
                ),
            ),
            "A?"
        ) { it.parseElement() }
    }

    @Test
    fun elementStar() {
        infrastructure.check(
            ElementNode(
                elementPrefix = null,
                ElementBody.ParserId(
                    tilde = null,
                    AntlrToken(AntlrTokenType.ParserId, value = "a"),
                ),
                elementSuffix = ElementSuffixNode(
                    AntlrToken(AntlrTokenType.Star),
                    nonGreedy = null
                )
            ),
            "a*"
        ) { it.parseElement() }
    }

    @Test
    fun elementPlus() {
        infrastructure.check(
            ElementNode(
                elementPrefix = null,
                ElementBody.LexerId(
                    tilde = null,
                    AntlrToken(AntlrTokenType.LexerId, value = "A"),
                ),
                elementSuffix = ElementSuffixNode(
                    AntlrToken(AntlrTokenType.Plus),
                    nonGreedy = null
                ),
            ),
            "A+"
        ) { it.parseElement() }
    }

    @Test
    fun elementNongreedy() {
        infrastructure.check(
            ElementNode(
                elementPrefix = null,
                ElementBody.ParserId(
                    tilde = null,
                    AntlrToken(AntlrTokenType.ParserId, value = "a"),
                ),
                elementSuffix = ElementSuffixNode(
                    AntlrToken(AntlrTokenType.Star),
                    AntlrToken(AntlrTokenType.Question),
                ),
            ),
            "a*?"
        ) { it.parseElement() }
    }

    @Test
    fun dot() {
        infrastructure.check(
            ElementNode(
                elementPrefix = null,
                ElementBody.Dot(
                    tilde = null,
                    AntlrToken(AntlrTokenType.Dot),
                ),
                elementSuffix = null,
            ),
            "."
        ) { it.parseElement() }
    }

    @Test
    fun tilde() {
        infrastructure.check(
            ElementNode(
                elementPrefix = null,
                ElementBody.LexerId(
                    tilde = AntlrToken(AntlrTokenType.Tilde),
                    AntlrToken(AntlrTokenType.LexerId, value = "A"),
                ),
                elementSuffix = null,
            ),
            "~A"
        ) { it.parseElement() }
    }

    @Test
    fun commands() {
        infrastructure.check(
            RuleNode(
                fragmentToken = null,
                AntlrToken(AntlrTokenType.LexerId, value = "A"),
                AntlrToken(AntlrTokenType.Colon),
                BlockNode(
                    alternativeNode = AlternativeNode(
                        listOf(
                            ElementNode(
                                elementPrefix = null,
                                ElementBody.StringLiteralOrRange(
                                    tilde = null,
                                    ElementBody.StringLiteralOrRange.StringLiteral(
                                        AntlrToken(AntlrTokenType.Quote),
                                        listOf(AntlrToken(AntlrTokenType.Char, value = "A")),
                                        AntlrToken(AntlrTokenType.Quote)
                                    ),
                                    range = null,
                                ),
                                elementSuffix = null
                            )
                        )
                    ),
                    orAlternativeNodes = emptyList(),
                ),
                CommandsNode(
                    AntlrToken(AntlrTokenType.RightArrow),
                    commandNode = CommandNode(
                        AntlrToken(AntlrTokenType.ParserId, value = "skip"),
                        paramsNode = null
                    ),
                    commaCommandNodes = listOf(
                        CommandsNode.CommaCommandNode(
                            AntlrToken(AntlrTokenType.Comma),
                            command = CommandNode(
                                AntlrToken(AntlrTokenType.ParserId, value = "pushMode"),
                                paramsNode = CommandNode.Params(
                                    AntlrToken(AntlrTokenType.LeftParen),
                                    AntlrToken(AntlrTokenType.LexerId, value = "DEFAULT_MODE"),
                                    AntlrToken(AntlrTokenType.RightParen)
                                )
                            )
                        )
                    ),
                ),
                AntlrToken(AntlrTokenType.Semicolon),
            ),
            "A: 'A' -> skip, pushMode(DEFAULT_MODE);"
        ) {
            it.parseRule()
        }
    }

    @Test
    fun labels() {
        infrastructure.check(
            RuleNode(
                fragmentToken = null,
                idToken = AntlrToken(AntlrTokenType.ParserId, value = "r"),
                colonToken = AntlrToken(AntlrTokenType.Colon),
                blockNode = BlockNode(
                    alternativeNode = AlternativeNode(
                        elementNodes = listOf(
                            ElementNode(
                                elementPrefix = ElementPrefixNode(
                                    AntlrToken(AntlrTokenType.ParserId, value = "label"),
                                    AntlrToken(AntlrTokenType.Equals),
                                ),
                                ElementBody.LexerId(
                                    tilde = null,
                                    AntlrToken(AntlrTokenType.LexerId, value = "A"),
                                ),
                                elementSuffix = null
                            ),
                            ElementNode(
                                elementPrefix = ElementPrefixNode(
                                    AntlrToken(AntlrTokenType.ParserId, value = "assignLabel"),
                                    AntlrToken(AntlrTokenType.PlusAssign),
                                ),
                                ElementBody.LexerId(
                                    tilde = null,
                                    AntlrToken(AntlrTokenType.LexerId, value = "B"),
                                ),
                                elementSuffix = null
                            )
                        )
                    ),
                    orAlternativeNodes = emptyList(),
                ),
                commandsNode = null,
                semicolonToken = AntlrToken(AntlrTokenType.Semicolon),
            ),
            "r: label=A assignLabel+=B;"
        ) {
            it.parseRule()
        }
    }
}