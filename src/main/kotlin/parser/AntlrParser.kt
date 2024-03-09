package parser

class AntlrParser(val tokenStream: AntlrTokenStream) {
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
        val lexerOrParserToken = when (getDefaultToken().type) {
            AntlrTokenType.Lexer -> matchDefaultToken(AntlrTokenType.Lexer)
            AntlrTokenType.Parser -> matchDefaultToken(AntlrTokenType.Parser)
            else -> null
        }

        val grammarToken = matchDefaultToken(AntlrTokenType.Grammar)

        val idToken = matchDefaultToken(AntlrTokenType.ParserId)

        val semicolonToken = matchDefaultToken(AntlrTokenType.Semicolon)

        val ruleNodes = buildList {
            var nextToken = getDefaultToken()
            while (nextToken.type in ruleTokenTypes) {
                add(parseRule())
                nextToken = getDefaultToken()
            }
        }

        return GrammarNode(lexerOrParserToken, grammarToken, idToken, semicolonToken, ruleNodes, emitEndNode(matchToEof))
    }

    // rule
    //   : (LexerId | ParserId) ':' block ';'
    //   ;
    fun parseRule(): RuleNode {
        val lexerIdOrParserIdToken = when (getDefaultToken().type) {
            AntlrTokenType.LexerId -> matchDefaultToken(AntlrTokenType.LexerId)
            AntlrTokenType.ParserId -> matchDefaultToken(AntlrTokenType.ParserId)
            else -> emitMissingToken(tokenType = null)
        }

        val colonToken = matchDefaultToken(AntlrTokenType.Colon)

        val blockNode = parseBlock()

        val semicolonToken = matchDefaultToken(AntlrTokenType.Semicolon)

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
            var nextToken = getDefaultToken()
            while (nextToken.type == AntlrTokenType.Bar) {
                matchDefaultToken(AntlrTokenType.Bar)
                add(BlockNode.OrAlternativeNode(nextToken, parseAlternative()))
                nextToken = getDefaultToken()
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
            while (getDefaultToken().type in elementTokenTypes) {
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
        val nextToken = getDefaultToken()

        fun tryParseElementSuffix(): ElementSuffixNode? {
            return if (getDefaultToken().type in elementSuffixTokenTypes) {
                parseElementSuffix()
            } else {
                null
            }
        }

        return when (nextToken.type) {
            AntlrTokenType.LexerId -> {
                ElementNode.LexerId(matchDefaultToken(AntlrTokenType.LexerId), tryParseElementSuffix(), emitEndNode(matchToEof))
            }
            AntlrTokenType.ParserId -> {
                ElementNode.ParserId(matchDefaultToken(AntlrTokenType.ParserId), tryParseElementSuffix(), emitEndNode(matchToEof))
            }
            AntlrTokenType.LeftParen -> ElementNode.Block(
                matchDefaultToken(AntlrTokenType.LeftParen),
                parseBlock(),
                matchDefaultToken(AntlrTokenType.RightParen),
                tryParseElementSuffix(),
                emitEndNode(matchToEof),
            )
            AntlrTokenType.Quote -> {
                val openQuote = matchDefaultToken(AntlrTokenType.Quote) // Consume quote
                var closeQuote: AntlrToken

                val charTokens = buildList {
                    while (true) {
                        val nextChar = getToken(0)
                        when (nextChar.type) {
                            AntlrTokenType.Quote -> {
                                closeQuote = nextChar
                                matchToken()
                                break
                            }
                            AntlrTokenType.LineBreak, AntlrTokenType.Eof -> {
                                closeQuote = AntlrToken(AntlrTokenType.Quote, nextChar.offset, 0, channel = AntlrTokenChannel.Error)
                                break
                            }
                            else -> {
                                add(matchToken())
                            }
                        }
                    }
                }

                ElementNode.StringLiteral(openQuote, charTokens, closeQuote, tryParseElementSuffix(), emitEndNode(matchToEof))
            }
            AntlrTokenType.LeftBracket -> {
                val openBracket = matchDefaultToken(AntlrTokenType.LeftBracket) // Consume quote
                var closeBracket: AntlrToken

                val charSetNodes = buildList {
                    while (true) {
                        val nextChar = getToken(0)
                        when {
                            nextChar.type == AntlrTokenType.RightBracket -> {
                                closeBracket = matchToken() // consume right bracket
                                break
                            }
                            nextChar.type == AntlrTokenType.LineBreak || nextChar.type == AntlrTokenType.Eof -> {
                                closeBracket = AntlrToken(AntlrTokenType.RightBracket, nextChar.offset, 0, channel = AntlrTokenChannel.Error)
                                break
                            }
                            getToken(1).type == AntlrTokenType.Hyphen && getToken(2).type in charSetTokenTypes -> {
                                add(ElementNode.CharSet.CharHyphenChar(
                                    matchToken(),
                                    ElementNode.CharSet.CharHyphenChar.HyphenChar(
                                        matchToken(),
                                        matchToken(),
                                    )
                                ))
                            }
                            else -> {
                                add(ElementNode.CharSet.CharHyphenChar(matchToken(), null))
                            }
                        }
                    }
                }

                ElementNode.CharSet(openBracket, charSetNodes, closeBracket, tryParseElementSuffix(), emitEndNode(matchToEof))
            }
            else -> {
                ElementNode.Empty(emitEndNode(matchToEof))
            }
        }
    }

    // elementSuffix
    //   : ('?' | '*' | '+') '?'?
    //   ;
    fun parseElementSuffix(): ElementSuffixNode {
        val ebnfToken = when (getDefaultToken().type) {
            AntlrTokenType.Question -> matchDefaultToken(AntlrTokenType.Question)
            AntlrTokenType.Star -> matchDefaultToken(AntlrTokenType.Star)
            AntlrTokenType.Plus -> matchDefaultToken(AntlrTokenType.Plus)
            else -> emitMissingToken(tokenType = null)
        }

        val nonGreedyToken = if (getDefaultToken().type == AntlrTokenType.Question) {
            matchDefaultToken(AntlrTokenType.Question)
        } else {
            null
        }

        return ElementSuffixNode(ebnfToken, nonGreedyToken)
    }

    private fun emitEndNode(matchToEof: Boolean): EndNode? {
        if (!matchToEof) {
            return null
        }

        val errorTokens = buildList {
            var nextToken = getDefaultToken()
            while (nextToken.type != AntlrTokenType.Eof) {
                add(matchDefaultToken(nextToken.type))
                nextToken = getDefaultToken()
            }
        }

        return EndNode(errorTokens, matchDefaultToken(AntlrTokenType.Eof))
    }

    private fun getDefaultToken(): AntlrToken {
        var currentToken: AntlrToken
        var currentOffset = 0
        do {
            currentToken = tokenStream.getToken(tokenIndex + currentOffset)
            if (currentToken.channel == AntlrTokenChannel.Default || currentToken.type == AntlrTokenType.Eof) {
                break
            }
            currentOffset++
        }
        while (true)

        return currentToken
    }

    private fun matchDefaultToken(tokenType: AntlrTokenType): AntlrToken {
        var currentToken: AntlrToken
        do {
            currentToken = tokenStream.getToken(tokenIndex)
            if (currentToken.channel == AntlrTokenChannel.Default || currentToken.type == AntlrTokenType.Eof) {
                if (tokenType != currentToken.type) {
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
        return AntlrToken(tokenType ?: AntlrTokenType.Error, getDefaultToken().offset, 0, channel = AntlrTokenChannel.Error)
    }

    private fun getToken(offset: Int): AntlrToken {
        return tokenStream.getToken(tokenIndex + offset)
    }

    private fun matchToken(): AntlrToken {
        return tokenStream.getToken(tokenIndex).also { if (it.type != AntlrTokenType.Eof) { tokenIndex++ } }
    }
}