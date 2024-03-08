package parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AntlrLeadingTrailingTokensTests {
    @Test
    fun empty() {
        val tokensCalculator = AntlrTokensCalculator(emptyList())
        checkTokens(0, tokensCalculator, emptyList(), leading = true)
        checkTokens(0, tokensCalculator, emptyList(), leading = false)
    }

    @Test
    fun single() {
        val tokensCalculator = AntlrTokensCalculator(listOf(AntlrToken(AntlrTokenType.ParserId, 0, 1)))
        checkTokens(0, tokensCalculator, emptyList(), leading = true)
        checkTokens(0, tokensCalculator, emptyList(), leading = false)
    }

    @Test
    fun simple() {
        val tokensCalculator = AntlrTokensCalculator(listOf(
            createWhitespaceToken(),
            AntlrToken(AntlrTokenType.ParserId),
            createWhitespaceToken(),
        ))
        checkTokens(1, tokensCalculator, listOf(createWhitespaceToken()), leading = true)
        checkTokens(1, tokensCalculator, listOf(createWhitespaceToken()), leading = false)
    }

    @Test
    fun onlyWhitespaces() {
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
    fun multipleTokens() {
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

        val tokenComparer = AntlrTreeComparer(null)

        for ((expectedToken, actualToken) in expected.zip(actualTokens)) {
            tokenComparer.compare(expectedToken, actualToken)
        }
    }

    private fun createWhitespaceToken() = AntlrToken(AntlrTokenType.Whitespace, channel = AntlrTokenChannel.Hidden)

    private fun createLineBreakToken() = AntlrToken(AntlrTokenType.LineBreak, channel = AntlrTokenChannel.Hidden)
}