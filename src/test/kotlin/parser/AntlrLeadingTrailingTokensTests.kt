package parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AntlrLeadingTrailingTokensTests {
    @Test
    fun testEmpty() {
        val tokensCalculator = AntlrTokensCalculator(emptyList())
        checkTokens(0, tokensCalculator, emptyList(), leading = true)
        checkTokens(0, tokensCalculator, emptyList(), leading = false)
    }

    @Test
    fun testSingle() {
        val tokensCalculator = AntlrTokensCalculator(listOf(AntlrToken(AntlrTokenType.ParserId, 0, 1)))
        checkTokens(0, tokensCalculator, emptyList(), leading = true)
        checkTokens(0, tokensCalculator, emptyList(), leading = false)
    }

    @Test
    fun testSimple() {
        val tokensCalculator = AntlrTokensCalculator(listOf(
            createWhitespaceToken(),
            AntlrToken(AntlrTokenType.ParserId),
            createWhitespaceToken(),
        ))
        checkTokens(1, tokensCalculator, listOf(createWhitespaceToken()), leading = true)
        checkTokens(1, tokensCalculator, listOf(createWhitespaceToken()), leading = false)
    }

    @Test
    fun testOnlyWhitespaces() {
        val tokensCalculator = AntlrTokensCalculator(listOf(
            createWhitespaceToken(),
            createLineBreakToken(),
            createWhitespaceToken(),
        ))
        checkTokens(3, tokensCalculator, listOf(
            createWhitespaceToken(),
            createLineBreakToken(),
            createWhitespaceToken(),
        ), leading = true)
        checkTokens(3, tokensCalculator, emptyList(), leading = false)
    }

    @Test
    fun testMultipleTokens() {
        val tokensCalculator = AntlrTokensCalculator(listOf(
            AntlrToken(AntlrTokenType.ParserId),
            createWhitespaceToken(),
            createLineBreakToken(),
            createWhitespaceToken(),
            createLineBreakToken(),
            createWhitespaceToken(),
            AntlrToken(AntlrTokenType.ParserId),
        ))
        checkTokens(0, tokensCalculator,
            listOf(createWhitespaceToken(), createLineBreakToken()),
            leading = false
        )
        checkTokens(6, tokensCalculator,
            listOf(createWhitespaceToken(), createLineBreakToken(), createWhitespaceToken()),
            leading = true
        )
    }

    private fun checkTokens(index: Int, tokensCalculator: AntlrTokensCalculator, expected: List<AntlrToken>, leading: Boolean) {
        val actualTokens = if (leading) {
            tokensCalculator.getLeadingTokens(index)
        } else {
            tokensCalculator.getTrailingTokens(index)
        }

        assertEquals(expected.size, actualTokens.size, "Size mismatch for ${if (leading) "leading" else "trailing"} tokens at index $index")

        for ((expectedToken, actualToken) in expected.zip(actualTokens)) {
            check(expectedToken, actualToken) { actualToken.value }
        }
    }

    private fun createWhitespaceToken() = AntlrToken(AntlrTokenType.Whitespace, channel = AntlrTokenChannel.Hidden)

    private fun createLineBreakToken() = AntlrToken(AntlrTokenType.LineBreak, channel = AntlrTokenChannel.Hidden)
}