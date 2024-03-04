class AntlrTokensCalculator(val tokens: List<AntlrToken>) {
    private val leadingTokensCache: MutableMap<Int, List<AntlrToken>> = mutableMapOf()
    private val trailingTokensCache: MutableMap<Int, List<AntlrToken>> = mutableMapOf()

    fun getLeadingTokens(index: Int): List<AntlrToken> {
        if (index < 0 || index > tokens.size) {
            throw IndexOutOfBoundsException("Index $index is out of bounds for tokens of size ${tokens.size}")
        }

        leadingTokensCache[index]?.let { return it }

        var startIndex = index - 1
        var lastLineBreakIndex = startIndex

        while (startIndex >= 0 && tokens[startIndex].channel != AntlrTokenChannel.Default) {
            if (tokens[startIndex].type == AntlrTokenType.LineBreak) {
                lastLineBreakIndex = startIndex
            }
            startIndex--
        }

        startIndex = if (startIndex == -1) startIndex else lastLineBreakIndex

        return tokens.subList(startIndex + 1, index).also { leadingTokensCache[index] = it }
    }

    fun getTrailingTokens(index: Int): List<AntlrToken> {
        if (index < 0 || index > tokens.size) {
            throw IndexOutOfBoundsException("Index $index is out of bounds for tokens of size ${tokens.size}")
        }

        trailingTokensCache[index]?.let { return it }

        var endIndex = index + 1

        while (endIndex < tokens.size && tokens[endIndex].let { it.channel != AntlrTokenChannel.Default && it.type != AntlrTokenType.LineBreak }) {
            endIndex++
        }

        val result = if (index + 1 >= tokens.size) {
            emptyList()
        } else {
            if (endIndex < tokens.size && tokens[endIndex].type == AntlrTokenType.LineBreak)
                endIndex += 1
            tokens.subList(index + 1, endIndex)
        }

        return result.also { trailingTokensCache[index] = it }
    }
}