package parser

import org.junit.jupiter.api.Test

class AntlrParseWithErrors {
    @Test
    fun elementWithErrors() {
        check(
            ElementNode.Empty(endNode = null),
            "+"
        ) { it.parseElement(matchToEof = false) }
    }

    @Test
    fun elementWithErrorsToEof() {
        check(
            ElementNode.Empty(
                EndNode(listOf(
                    AntlrToken(AntlrTokenType.Plus, channel = AntlrTokenChannel.Default)
                ), AntlrToken(AntlrTokenType.Eof))
            ),
            "+"
        ) { it.parseElement(matchToEof = true) }
    }

    @Test
    fun grammarWithErrorsToEof() {
        check(
            GrammarNode(
                null,
                AntlrToken(AntlrTokenType.Grammar, value = "grammar"),
                AntlrToken(AntlrTokenType.ParserId, channel = AntlrTokenChannel.Error),
                AntlrToken(AntlrTokenType.Semicolon, channel = AntlrTokenChannel.Error),
                emptyList(),
                EndNode(
                    listOf(
                        AntlrToken(AntlrTokenType.Plus),
                        AntlrToken(AntlrTokenType.ParserId),
                    ),
                    AntlrToken(AntlrTokenType.Eof)
                )
            ),
            "grammar + test"
        ) { it.parseGrammar(matchToEof = true) }
    }
}