package parser

import org.junit.jupiter.api.Test

object AntlrParserWithLexerTests {
    @Test
    fun parserWithLexer() {
        infrastructure.check(ExampleData.TreeNode, ExampleData.GRAMMAR) { it.parseGrammar() }
    }

    @Test
    fun stringLiteral() {
        infrastructure.check(
            ElementNode.StringLiteralOrRange(
                tilde = null,
                ElementNode.StringLiteralOrRange.StringLiteral(
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
                elementSuffix = null,
            ),
            """'ab\r\t\u000A'"""
        ) { it.parseElement() }
    }

    @Test
    fun range() {
        infrastructure.check(
            ElementNode.StringLiteralOrRange(
                tilde = null,
                ElementNode.StringLiteralOrRange.StringLiteral(
                    AntlrToken(AntlrTokenType.Quote),
                    listOf(AntlrToken(AntlrTokenType.Char, value = "a")),
                    AntlrToken(AntlrTokenType.Quote)
                ),
                range = ElementNode.StringLiteralOrRange.Range(
                    AntlrToken(AntlrTokenType.Range),
                    ElementNode.StringLiteralOrRange.StringLiteral(
                        AntlrToken(AntlrTokenType.Quote),
                        listOf(AntlrToken(AntlrTokenType.Char, value = "z")),
                        AntlrToken(AntlrTokenType.Quote)
                    )
                ),
                elementSuffix = null,
            ),
            """'a'..'z'"""
        ) { it.parseElement() }
    }

    @Test
    fun unterminatedStringLiteral() {
        infrastructure.check(
            ElementNode.StringLiteralOrRange(
                tilde = null,
                ElementNode.StringLiteralOrRange.StringLiteral(
                    AntlrToken(AntlrTokenType.Quote),
                    listOf(AntlrToken(AntlrTokenType.Char, value = "a")),
                    AntlrToken(AntlrTokenType.Quote, channel = AntlrTokenChannel.Error),
                ),
                range = null,
                elementSuffix = null,
            ),
            """'a"""
        ) { it.parseElement() }
    }

    @Test
    fun stringLiteralWithIncorrectEscaping() {
        infrastructure.check(
            ElementNode.StringLiteralOrRange(
                tilde = null,
                ElementNode.StringLiteralOrRange.StringLiteral(
                    AntlrToken(AntlrTokenType.Quote),
                    // Unrecognized token '\u' can be extract from hidden channel
                    listOf(AntlrToken(AntlrTokenType.Char, value = "X")),
                    AntlrToken(AntlrTokenType.Quote),
                ),
                range = null,
                elementSuffix = null,
            ),
            """'\uX'"""
        ) { it.parseElement() }
    }

    @Test
    fun charSet() {
        infrastructure.check(
            ElementNode.CharSet(
                tilde = null,
                AntlrToken(AntlrTokenType.LeftBracket),
                listOf(
                    ElementNode.CharSet.CharOrRange(
                        AntlrToken(AntlrTokenType.Hyphen, value = "-"),
                        range = null
                    ),
                    ElementNode.CharSet.CharOrRange(
                        AntlrToken(AntlrTokenType.Char, value = "a"),
                        ElementNode.CharSet.CharOrRange.Range(
                            AntlrToken(AntlrTokenType.Hyphen),
                            AntlrToken(AntlrTokenType.Char, value = "z")
                        )
                    ),
                    ElementNode.CharSet.CharOrRange(
                        AntlrToken(AntlrTokenType.Char, value = "0"),
                        ElementNode.CharSet.CharOrRange.Range(
                            AntlrToken(AntlrTokenType.Hyphen),
                            AntlrToken(AntlrTokenType.Char, value = "9")
                        )
                    ),
                    ElementNode.CharSet.CharOrRange(
                        AntlrToken(AntlrTokenType.Char, value = "Z"),
                        range = null
                    ),
                    ElementNode.CharSet.CharOrRange(
                        AntlrToken(AntlrTokenType.Hyphen, value = "-"),
                        range = null
                    ),
                ),
                AntlrToken(AntlrTokenType.RightBracket),
                elementSuffix = null,
            ),
            """[-a-z0-9Z-]"""
        ) { it.parseElement() }
    }

    @Test
    fun unterminatedCharSet() {
        infrastructure.check(
            ElementNode.CharSet(
                tilde = null,
                AntlrToken(AntlrTokenType.LeftBracket),
                listOf(
                    ElementNode.CharSet.CharOrRange(
                        AntlrToken(AntlrTokenType.Char, value = "a"),
                        range = null
                    ),
                    ElementNode.CharSet.CharOrRange(
                        AntlrToken(AntlrTokenType.Hyphen, value = "-"),
                        range = null
                    ),
                ),
                AntlrToken(AntlrTokenType.RightBracket, channel = AntlrTokenChannel.Error),
                elementSuffix = null,
            ),
            "[a-\n"
        ) { it.parseElement() }
    }

    @Test
    fun elementSuffix() {
        infrastructure.check(
            AlternativeNode(
                listOf(
                    ElementNode.LexerId(
                        tilde = null,
                        AntlrToken(AntlrTokenType.LexerId, value = "A"),
                        elementSuffix = ElementSuffixNode(
                            AntlrToken(AntlrTokenType.Question),
                            nonGreedy = null
                        ),
                    ),
                    ElementNode.ParserId(
                        tilde = null,
                        AntlrToken(AntlrTokenType.ParserId, value = "a"),
                        elementSuffix = ElementSuffixNode(
                            AntlrToken(AntlrTokenType.Star),
                            nonGreedy = null
                        ),
                    ),
                    ElementNode.LexerId(
                        tilde = null,
                        AntlrToken(AntlrTokenType.LexerId, value = "A"),
                        elementSuffix = ElementSuffixNode(
                            AntlrToken(AntlrTokenType.Plus),
                            nonGreedy = null
                        ),
                    ),
                    ElementNode.ParserId(
                        tilde = null,
                        AntlrToken(AntlrTokenType.ParserId, value = "a"),
                        elementSuffix = ElementSuffixNode(
                            AntlrToken(AntlrTokenType.Star),
                            AntlrToken(AntlrTokenType.Question),
                        ),
                    ),
                )
            ),
            "A? a* A+ a*?"
        ) { it.parseAlternative() }
    }

    @Test
    fun dot() {
        infrastructure.check(
            ElementNode.Dot(
                tilde = null,
                AntlrToken(AntlrTokenType.Dot),
                elementSuffix = null,
            ),
            "."
        ) { it.parseElement() }
    }

    @Test
    fun tilde() {
        infrastructure.check(
            ElementNode.LexerId(
                tilde = AntlrToken(AntlrTokenType.Tilde),
                AntlrToken(AntlrTokenType.LexerId, value = "A"),
                elementSuffix = null,
            ),
            "~A"
        ) { it.parseElement() }
    }
}