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
        val expectedNode = ElementNode.StringLiteral(
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
        )

        check(expectedNode, """'ab\r\t\u000A'""") { it.parseElement() }
    }

    @Test
    fun unterminatedStringLiteral() {
        val expectedNode = ElementNode.StringLiteral(
            AntlrToken(AntlrTokenType.Quote),
            listOf(AntlrToken(AntlrTokenType.Char, value = "a")),
            AntlrToken(AntlrTokenType.Quote, channel = AntlrTokenChannel.Error),
            elementSuffix = null,
        )

        check(expectedNode, """'a""") { it.parseElement() }
    }

    @Test
    fun charSet() {
        val expectedNode = ElementNode.CharSet(
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
        )

        check(expectedNode, """[-a-z0-9Z-]""") { it.parseElement() }
    }

    @Test
    fun unterminatedCharSet() {
        val expectedNode = ElementNode.CharSet(
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
        )

        check(expectedNode, "[a-\n") { it.parseElement() }
    }

    @Test
    fun elementSuffix() {
        val expectedNode = AlternativeNode(listOf(
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
        ))

        check(expectedNode, "A? a* A+ a*?") { it.parseAlternative() }
    }

    private fun <T> check(expectedTreeFragment: T, grammarFragment: String, parseFunc: (AntlrParser) -> T) {
        val lexer = AntlrLexer(grammarFragment)
        val tokenStream = AntlrLexerTokenStream(lexer)
        val parser = AntlrParser(tokenStream)
        val actualNode = parseFunc(parser)

        AntlrTreeComparer(lexer).compare(expectedTreeFragment, actualNode)
    }
}