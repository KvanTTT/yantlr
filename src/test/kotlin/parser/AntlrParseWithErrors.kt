package parser

import org.junit.jupiter.api.Test

object AntlrParseWithErrors {
    @Test
    fun elementWithErrors() {
        infrastructure.check(
            ElementNode.Empty(
                AntlrToken(AntlrTokenType.Empty, 0, 0),
                endNode = null
            ),
            "+"
        ) { it.parseElement(matchToEof = false) }
    }

    @Test
    fun elementWithErrorsToEof() {
        infrastructure.check(
            ElementNode.Empty(
                AntlrToken(AntlrTokenType.Empty, 0, 0),
                EndNode(
                    listOf(
                        AntlrToken(AntlrTokenType.Plus, channel = AntlrTokenChannel.Default)
                    ), AntlrToken(AntlrTokenType.Eof)
                )
            ),
            "+"
        ) { it.parseElement(matchToEof = true) }
    }

    @Test
    fun grammarWithErrorsToEof() {
        infrastructure.check(
            GrammarNode(
                null,
                AntlrToken(AntlrTokenType.Grammar),
                AntlrToken(AntlrTokenType.Error, channel = AntlrTokenChannel.Error),
                AntlrToken(AntlrTokenType.Semicolon, channel = AntlrTokenChannel.Error),
                emptyList(),
                emptyList(),
                EndNode(
                    listOf(
                        AntlrToken(AntlrTokenType.Plus),
                        AntlrToken(AntlrTokenType.ParserId),
                    ),
                    AntlrToken(AntlrTokenType.Eof)
                )
            ),
            "grammar ` + test"
        ) { it.parseGrammar(matchToEof = true) }
    }
}