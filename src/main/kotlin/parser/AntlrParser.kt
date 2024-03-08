package parser

class AntlrParser(val tokenStream: AntlrTokenStream) {
    companion object {
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
    //   : (Lexer | Parser)? Grammar ParserId Semicolon rule*
    //   ;
    fun parseGrammar(): GrammarNode {
        val token = matchDefaultToken()
        val lexerOrParserToken = when (token.type) {
            AntlrTokenType.Lexer -> {
                matchDefaultToken()
            }
            AntlrTokenType.Parser -> {
                matchDefaultToken()
            }
            else -> null
        }

        val grammarToken = if (lexerOrParserToken == null) token else matchDefaultToken()

        val idToken = matchDefaultToken()

        val semicolonToken = matchDefaultToken()

        var nextToken = getDefaultToken()
        val ruleNodes = buildList {
            while (nextToken.type != AntlrTokenType.Eof) {
                add(parseRule())
                nextToken = getDefaultToken()
            }
        }

        return GrammarNode(lexerOrParserToken, grammarToken, idToken, semicolonToken, ruleNodes, nextToken)
    }

    // rule
    //   : (LexerId | ParserId) Colon block Semicolon
    //   ;
    fun parseRule(): RuleNode {
        val lexerIdOrParserIdToken = matchDefaultToken()

        val colonToken = matchDefaultToken()

        val blockNode = parseBlock()

        val semicolonToken = matchDefaultToken()

        return RuleNode(
            lexerIdOrParserIdToken,
            colonToken,
            blockNode,
            semicolonToken
        )
    }

    // block
    //   : alternative (OR alternative)*
    //   ;
    fun parseBlock(): BlockNode {
        val alternativeNode = parseAlternative()

        val barAlternativeChildren = buildList {
            var nextToken = getDefaultToken()
            while (nextToken.type == AntlrTokenType.Bar) {
                matchDefaultToken()
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
            add(parseElement())
            while (getDefaultToken().type in elementTokenTypes) {
                add(parseElement())
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
    fun parseElement(): ElementNode {
        val nextToken = getDefaultToken()

        fun tryParseElementSuffix(): ElementSuffixNode? {
            return if (getDefaultToken().type in elementSuffixTokenTypes) {
                parseElementSuffix()
            } else {
                null
            }
        }

        return when (nextToken.type) {
            AntlrTokenType.LexerId -> ElementNode.LexerId(matchDefaultToken(), tryParseElementSuffix())
            AntlrTokenType.ParserId -> ElementNode.ParserId(matchDefaultToken(), tryParseElementSuffix())
            AntlrTokenType.LeftParen -> ElementNode.Block(
                matchDefaultToken(),
                parseBlock(),
                matchDefaultToken(),
                tryParseElementSuffix()
            )
            AntlrTokenType.Quote -> {
                val openQuote = matchDefaultToken() // Consume quote
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

                ElementNode.StringLiteral(openQuote, charTokens, closeQuote, tryParseElementSuffix())
            }
            AntlrTokenType.LeftBracket -> {
                val openBracket = matchDefaultToken() // Consume quote
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

                ElementNode.CharSet(openBracket, charSetNodes, closeBracket, tryParseElementSuffix())
            }
            else -> {
                ElementNode.Empty()
            }
        }
    }

    // elementSuffix
    //   : ('?' | '*' | '+') '?'?
    //   ;
    fun parseElementSuffix(): ElementSuffixNode {
        val ebnfToken = matchDefaultToken()
        val nonGreedyToken = if (getDefaultToken().type == AntlrTokenType.Question) {
            matchDefaultToken()
        } else {
            null
        }

        return ElementSuffixNode(ebnfToken, nonGreedyToken)
    }

    private fun getDefaultToken(): AntlrToken {
        var currentToken: AntlrToken
        var currentOffset = 0
        do {
            currentToken = tokenStream.getToken(tokenIndex + currentOffset)
            currentOffset++
        }
        while (currentToken.channel != AntlrTokenChannel.Default)

        return currentToken
    }

    private fun matchDefaultToken(): AntlrToken {
        var currentToken: AntlrToken
        do {
            currentToken = tokenStream.getToken(tokenIndex)
            tokenIndex++
        }
        while (currentToken.channel != AntlrTokenChannel.Default)

        return currentToken
    }

    private fun getToken(offset: Int): AntlrToken {
        return tokenStream.getToken(tokenIndex + offset)
    }

    private fun matchToken(): AntlrToken {
        return tokenStream.getToken(tokenIndex).also { tokenIndex++ }
    }
}