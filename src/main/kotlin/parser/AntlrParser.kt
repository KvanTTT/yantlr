package parser

import ExtraToken
import MissingToken
import ParserDiagnostic

class AntlrParser(
    val tokenStream: AntlrTokenStream,
    val diagnosticReporter: ((ParserDiagnostic) -> Unit)? = null
) {
    companion object {
        private val ruleTokenTypes = setOf(
            AntlrTokenType.LexerId,
            AntlrTokenType.ParserId,
        )

        private val elementTokenTypes = setOf(
            AntlrTokenType.LexerId,
            AntlrTokenType.ParserId,
            AntlrTokenType.LeftParen,
            AntlrTokenType.Quote,
            AntlrTokenType.LeftBracket,
        )

        private val charSetTokenTypes = setOf(
            AntlrTokenType.Char,
            AntlrTokenType.EscapedChar,
            AntlrTokenType.UnicodeEscapedChar,
            AntlrTokenType.Hyphen,
        )

        private val elementSuffixTokenTypes = setOf(
            AntlrTokenType.Question,
            AntlrTokenType.Star,
            AntlrTokenType.Plus,
        )
    }

    var tokenIndex: Int = 0
        private set
        get

    // grammar
    //   : (Lexer | Parser)? Grammar ParserId ';' rule*
    //   ;
    fun parseGrammar(matchToEof: Boolean = true): GrammarNode {
        val lexerOrParserToken = when (getToken().type) {
            AntlrTokenType.Lexer, AntlrTokenType.Parser -> matchToken()
            else -> null
        }

        val grammarToken = matchToken(AntlrTokenType.Grammar)

        val idToken = matchToken(AntlrTokenType.ParserId)

        val semicolonToken = matchToken(AntlrTokenType.Semicolon)

        val ruleNodes = buildList {
            var nextToken = getToken()
            while (nextToken.type in ruleTokenTypes) {
                add(parseRule())
                nextToken = getToken()
            }
        }

        val endToken = emitEndNode(emptyList(), matchToEof)

        return GrammarNode(lexerOrParserToken, grammarToken, idToken, semicolonToken, ruleNodes, endToken)
    }

    // rule
    //   : (LexerId | ParserId) ':' block ';'
    //   ;
    fun parseRule(): RuleNode {
        val lexerIdOrParserIdToken = when (getToken().type) {
            AntlrTokenType.LexerId, AntlrTokenType.ParserId -> matchToken()
            else -> emitMissingToken(tokenType = null)
        }

        val colonToken = matchToken(AntlrTokenType.Colon)

        val blockNode = parseBlock()

        val semicolonToken = matchToken(AntlrTokenType.Semicolon)

        return RuleNode(
            lexerIdOrParserIdToken,
            colonToken,
            blockNode,
            semicolonToken
        )
    }

    // block
    //   : alternative ('|' alternative)*
    //   ;
    fun parseBlock(): BlockNode {
        val alternativeNode = parseAlternative()

        val barAlternativeChildren = buildList {
            var nextToken = getToken()
            while (nextToken.type == AntlrTokenType.Bar) {
                matchToken()
                add(BlockNode.OrAlternativeNode(nextToken, parseAlternative()))
                nextToken = getToken()
            }
        }

        return BlockNode(alternativeNode, barAlternativeChildren)
    }

    // alternative
    //   : element+
    //   ;
    fun parseAlternative(): AlternativeNode {
        val elementNodes = buildList {
            add(parseElement(false))
            while (getToken().type in elementTokenTypes) {
                add(parseElement(false))
            }
        }

        return AlternativeNode(elementNodes)
    }

    // element
    //   : LexerId elementSuffix?
    //   | ParserId elementSuffix?
    //   | '(' block? ')' elementSuffix?
    //   | '\'' char* '\'' elementSuffix?
    //   | '[' (char range=('-' char)?)* ']' elementSuffix?
    //   |
    //   ;
    //
    // char
    //   : Char | EscapedChar | UnicodeEscapedChar
    //   ;
    fun parseElement(matchToEof: Boolean = false): ElementNode {
        val nextToken = getToken()

        fun tryParseElementSuffix(): ElementSuffixNode? {
            return if (getToken().type in elementSuffixTokenTypes) {
                parseElementSuffix()
            } else {
                null
            }
        }

        val extraTokens = mutableListOf<AntlrToken>()
        return when (nextToken.type) {
            AntlrTokenType.LexerId -> {
                ElementNode.LexerId(matchToken(), tryParseElementSuffix(), emitEndNode(extraTokens, matchToEof))
            }
            AntlrTokenType.ParserId -> {
                ElementNode.ParserId(matchToken(), tryParseElementSuffix(), emitEndNode(extraTokens, matchToEof))
            }
            AntlrTokenType.LeftParen -> ElementNode.Block(
                matchToken(),
                parseBlock(),
                matchToken(AntlrTokenType.RightParen),
                tryParseElementSuffix(),
                emitEndNode(extraTokens, matchToEof),
            )
            AntlrTokenType.Quote -> {
                val openQuote = matchToken() // Consume quote
                var closeQuote: AntlrToken

                val charTokens = buildList {
                    while (true) {
                        when (getToken().type) {
                            AntlrTokenType.Quote -> {
                                closeQuote = matchToken()
                                break
                            }
                            AntlrTokenType.LineBreak, AntlrTokenType.Eof -> {
                                closeQuote = AntlrToken(AntlrTokenType.Quote, getToken().offset, 0, channel = AntlrTokenChannel.Error)
                                break
                            }
                            AntlrTokenType.Char, AntlrTokenType.EscapedChar, AntlrTokenType.UnicodeEscapedChar -> {
                                add(matchToken())
                            }
                            else -> {
                                extraTokens.add(matchToken())
                            }
                        }
                    }
                }

                ElementNode.StringLiteral(
                    openQuote,
                    charTokens,
                    closeQuote,
                    tryParseElementSuffix(),
                    emitEndNode(extraTokens, matchToEof)
                )
            }
            AntlrTokenType.LeftBracket -> {
                val openBracket = matchToken() // Consume quote
                var closeBracket: AntlrToken

                val charSetNodes = buildList {
                    while (true) {
                        when (getToken(0).type) {
                            AntlrTokenType.RightBracket -> {
                                closeBracket = matchToken() // consume right bracket
                                break
                            }
                            AntlrTokenType.LineBreak, AntlrTokenType.Eof -> {
                                closeBracket = AntlrToken(AntlrTokenType.RightBracket, getToken().offset, 0, channel = AntlrTokenChannel.Error)
                                break
                            }
                            AntlrTokenType.Hyphen, AntlrTokenType.Char, AntlrTokenType.EscapedChar, AntlrTokenType.UnicodeEscapedChar -> {
                                if (getToken(1).type == AntlrTokenType.Hyphen && getToken(2).type in charSetTokenTypes) {
                                    add(ElementNode.CharSet.CharHyphenChar(
                                        matchToken(),
                                        ElementNode.CharSet.CharHyphenChar.HyphenChar(
                                            matchToken(),
                                            matchToken(),
                                        )
                                    ))
                                } else {
                                    add(ElementNode.CharSet.CharHyphenChar(matchToken(), null))
                                }
                            }
                            else -> {
                                extraTokens.add(matchToken())
                            }
                        }
                    }
                }

                ElementNode.CharSet(
                    openBracket,
                    charSetNodes,
                    closeBracket,
                    tryParseElementSuffix(),
                    emitEndNode(extraTokens, matchToEof)
                )
            }
            else -> {
                ElementNode.Empty(emitEndNode(extraTokens, matchToEof))
            }
        }
    }

    // elementSuffix
    //   : ('?' | '*' | '+') '?'?
    //   ;
    fun parseElementSuffix(): ElementSuffixNode {
        val ebnfToken = when (getToken().type) {
            AntlrTokenType.Question, AntlrTokenType.Star, AntlrTokenType.Plus -> matchToken()
            else -> emitMissingToken(tokenType = null)
        }

        val nonGreedyToken = if (getToken().type == AntlrTokenType.Question) {
            matchToken()
        } else {
            null
        }

        return ElementSuffixNode(ebnfToken, nonGreedyToken)
    }

    private fun emitEndNode(extraTokens: List<AntlrToken>, matchToEof: Boolean): EndNode? {
        for (extraToken in extraTokens) {
            diagnosticReporter?.invoke(ExtraToken(extraToken, extraToken.offset, extraToken.length))
        }

        if (!matchToEof) {
            return extraTokens.takeIf { it.isNotEmpty() }?.let { EndNode(it, null) }
        }

        val errorTokens = extraTokens + buildList {
            var nextToken = getToken()
            while (nextToken.type != AntlrTokenType.Eof) {
                diagnosticReporter?.invoke(ExtraToken(nextToken, nextToken.offset, nextToken.length))
                add(matchToken(nextToken.type))
                nextToken = getToken()
            }
        }

        return EndNode(errorTokens, matchToken(AntlrTokenType.Eof))
    }

    private fun getToken(offset: Int = 0): AntlrToken {
        var currentToken: AntlrToken
        var currentOffset = 0
        var currentOffsetInDefaultChannel = 0
        do {
            currentToken = tokenStream.getToken(tokenIndex + currentOffset)
            if (currentToken.channel == AntlrTokenChannel.Default) {
                if (currentOffsetInDefaultChannel == offset) {
                    break
                }
                currentOffsetInDefaultChannel++
            }
            if (currentToken.type == AntlrTokenType.Eof) {
                break
            }
            currentOffset++
        }
        while (true)

        return currentToken
    }

    // If tokenType is null, it unconditionally matches any token
    private fun matchToken(tokenType: AntlrTokenType? = null): AntlrToken {
        var currentToken: AntlrToken
        do {
            currentToken = tokenStream.getToken(tokenIndex)
            if (currentToken.channel == AntlrTokenChannel.Default || currentToken.type == AntlrTokenType.Eof) {
                if (tokenType != null && tokenType != currentToken.type) {
                    currentToken = emitMissingToken(tokenType)
                } else if (currentToken.type != AntlrTokenType.Eof) {
                    tokenIndex++
                }
                break
            } else {
                tokenIndex++
            }
        }
        while (true)

        return currentToken
    }

    private fun emitMissingToken(tokenType: AntlrTokenType?): AntlrToken {
        // TODO: handle multiple token types (when tokenType is null)
        return AntlrToken(tokenType ?: AntlrTokenType.Error, getToken().offset, 0, channel = AntlrTokenChannel.Error)
            .also { diagnosticReporter?.invoke(MissingToken(it, it.offset, 0)) }
    }
}