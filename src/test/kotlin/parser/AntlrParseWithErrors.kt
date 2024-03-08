package parser

import org.junit.jupiter.api.Test

class AntlrParseWithErrors {
    @Test
    fun elementWithErrors() {
        check(
            ElementNode.Empty(eofNode = null),
            "+"
        ) { it.parseElement(matchToEof = false) }
    }

    @Test
    fun elementWithErrorsToEof() {
        check(
            ElementNode.Empty(
                EofNode(listOf(
                    AntlrToken(AntlrTokenType.Plus, channel = AntlrTokenChannel.Default)
                ), AntlrToken(AntlrTokenType.Eof))
            ),
            "+"
        ) { it.parseElement(matchToEof = true) }
    }
}