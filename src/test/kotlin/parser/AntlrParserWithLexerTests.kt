package parser

import org.junit.jupiter.api.Test
import parser.AntlrParserTests.Companion.defaultTreeNode

class AntlrParserWithLexerTests {
    companion object {
        val defaultGrammar = """
// Begin comment
grammar test;
x
    : A
    | b
    | (C | d)
    |
    ;
// End comment
""".trimIndent()
    }

    @Test
    fun parserWithLexer() {
        check(defaultTreeNode, defaultGrammar) { it.parseGrammar() }
    }

    @Test
    fun stringLiteral() {
        check(
            ElementNode.StringLiteral(
                AntlrToken(AntlrTokenType.Quote),
                listOf(
                    AntlrToken(AntlrTokenType.Char, value = "a"),
                    AntlrToken(AntlrTokenType.Char, value = "b"),
                    AntlrToken(AntlrTokenType.EscapedChar, value = "\\r"),
                    AntlrToken(AntlrTokenType.EscapedChar, value = "\\t"),
                    AntlrToken(AntlrTokenType.UnicodeEscapedChar, value = "\\u000A"),
                ),
                AntlrToken(AntlrTokenType.Quote),
                elementSuffix = null,
            ),
            """'ab\r\t\u000A'"""
        ) { it.parseElement() }
    }

    @Test
    fun unterminatedStringLiteral() {
        check(
            ElementNode.StringLiteral(
                AntlrToken(AntlrTokenType.Quote),
                listOf(AntlrToken(AntlrTokenType.Char, value = "a")),
                AntlrToken(AntlrTokenType.Quote, channel = AntlrTokenChannel.Error),
                elementSuffix = null,
            ),
            """'a"""
        ) { it.parseElement() }
    }

    @Test
    fun stringLiteralWithIncorrectEscaping() {
        check(
            ElementNode.StringLiteral(
                AntlrToken(AntlrTokenType.Quote),
                // Unrecognized token '\u' can be extract from hidden channel
                listOf(AntlrToken(AntlrTokenType.Char, value = "X")),
                AntlrToken(AntlrTokenType.Quote),
                elementSuffix = null,
            ),
            """'\uX'"""
        ) { it.parseElement() }
    }

    @Test
    fun charSet() {
        check(
            ElementNode.CharSet(
                AntlrToken(AntlrTokenType.LeftBracket),
                listOf(
                    ElementNode.CharSet.CharHyphenChar(
                        AntlrToken(AntlrTokenType.Hyphen, value = "-"),
                        range = null
                    ),
                    ElementNode.CharSet.CharHyphenChar(
                        AntlrToken(AntlrTokenType.Char, value = "a"),
                        ElementNode.CharSet.CharHyphenChar.HyphenChar(
                            AntlrToken(AntlrTokenType.Hyphen),
                            AntlrToken(AntlrTokenType.Char, value = "z")
                        )
                    ),
                    ElementNode.CharSet.CharHyphenChar(
                        AntlrToken(AntlrTokenType.Char, value = "0"),
                        ElementNode.CharSet.CharHyphenChar.HyphenChar(
                            AntlrToken(AntlrTokenType.Hyphen),
                            AntlrToken(AntlrTokenType.Char, value = "9")
                        )
                    ),
                    ElementNode.CharSet.CharHyphenChar(
                        AntlrToken(AntlrTokenType.Char, value = "Z"),
                        range = null
                    ),
                    ElementNode.CharSet.CharHyphenChar(
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
        check(
            ElementNode.CharSet(
                AntlrToken(AntlrTokenType.LeftBracket),
                listOf(
                    ElementNode.CharSet.CharHyphenChar(
                        AntlrToken(AntlrTokenType.Char, value = "a"),
                        range = null
                    ),
                    ElementNode.CharSet.CharHyphenChar(
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
        check(
            AlternativeNode(listOf(
                ElementNode.LexerId(
                    AntlrToken(AntlrTokenType.LexerId, value = "A"),
                    elementSuffix = ElementSuffixNode(
                        AntlrToken(AntlrTokenType.Question),
                        nonGreedy = null
                    ),
                ),
                ElementNode.ParserId(
                    AntlrToken(AntlrTokenType.ParserId, value = "a"),
                    elementSuffix = ElementSuffixNode(
                        AntlrToken(AntlrTokenType.Star),
                        nonGreedy = null
                    ),
                ),
                ElementNode.LexerId(
                    AntlrToken(AntlrTokenType.LexerId, value = "A"),
                    elementSuffix = ElementSuffixNode(
                        AntlrToken(AntlrTokenType.Plus),
                        nonGreedy = null
                    ),
                ),
                ElementNode.ParserId(
                    AntlrToken(AntlrTokenType.ParserId, value = "a"),
                    elementSuffix = ElementSuffixNode(
                        AntlrToken(AntlrTokenType.Star),
                        AntlrToken(AntlrTokenType.Question),
                    ),
                ),
            )),
            "A? a* A+ a*?"
        ) { it.parseAlternative() }
    }
}