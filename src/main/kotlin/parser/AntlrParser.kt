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

        var (nextToken, _) = getDefaultToken()
        val ruleNodes = buildList {
            while (nextToken.type != AntlrTokenType.Eof) {
                add(parseRule())
                nextToken = getDefaultToken().first
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
            var (nextToken, offset) = getDefaultToken()
            while (nextToken.type == AntlrTokenType.Bar) {
                incTokenIndex(offset)
                add(BlockNode.OrAlternativeNode(nextToken, parseAlternative()))
                val token = getDefaultToken()
                nextToken = token.first
                offset = token.second
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
            while (getDefaultToken().first.type in elementTokenTypes) {
                add(parseElement())
            }
        }

        return AlternativeNode(elementNodes)
    }

    // element
    //   : LexerId
    //   | ParserId
    //   | '(' block? ')'
    //   | '\'' char* '\''
    //   | '[' (char range=('-' char)?)* ']'
    //   |
    //   ;
    //
    // char
    //   : Char | EscapedChar | UnicodeEscapedChar
    //   ;
    fun parseElement(): ElementNode {
        val (nextToken, offset) = getDefaultToken()
        return when (nextToken.type) {
            AntlrTokenType.LexerId -> ElementNode.LexerId(nextToken).also { incTokenIndex(offset) }
            AntlrTokenType.ParserId -> ElementNode.ParserId(nextToken).also { incTokenIndex(offset) }
            AntlrTokenType.LeftParen -> ElementNode.Block(
                nextToken.also { incTokenIndex(offset) },
                parseBlock(),
                matchDefaultToken()
            )
            AntlrTokenType.Quote -> {
                incTokenIndex(offset) // Consume quote
                var closeQuote: AntlrToken

                val charTokens = buildList {
                    while (true) {
                        val nextChar = getToken(0)
                        when (nextChar.type) {
                            AntlrTokenType.Quote -> {
                                closeQuote = nextChar
                                incTokenIndex(1)
                                break
                            }
                            AntlrTokenType.LineBreak, AntlrTokenType.Eof -> {
                                closeQuote = AntlrToken(AntlrTokenType.Quote, nextChar.offset, 0, channel = AntlrTokenChannel.Error)
                                break
                            }
                            else -> {
                                add(nextChar)
                                incTokenIndex(1)
                            }
                        }
                    }
                }

                ElementNode.StringLiteral(nextToken, charTokens, closeQuote)
            }
            AntlrTokenType.LeftBracket -> {
                incTokenIndex(offset) // Consume quote
                var closeBracket: AntlrToken

                val charSetNodes = buildList {
                    while (true) {
                        val nextChar = getToken(0)
                        when {
                            nextChar.type == AntlrTokenType.RightBracket -> {
                                closeBracket = nextChar
                                incTokenIndex(1) // consume right bracket
                                break
                            }
                            nextChar.type == AntlrTokenType.LineBreak || nextChar.type == AntlrTokenType.Eof -> {
                                closeBracket = AntlrToken(AntlrTokenType.RightBracket, nextChar.offset, 0, channel = AntlrTokenChannel.Error)
                                break
                            }
                            getToken(1).type == AntlrTokenType.Hyphen && getToken(2).type in charSetTokenTypes -> {
                                incTokenIndex(1) // consume char
                                add(ElementNode.CharSet.CharHyphenChar(nextChar,
                                    ElementNode.CharSet.CharHyphenChar.HyphenChar(
                                        matchToken(),
                                        matchToken(),
                                    )
                                ))
                            }
                            else -> {
                                add(ElementNode.CharSet.CharHyphenChar(nextChar, null))
                                incTokenIndex(1)
                            }
                        }
                    }
                }

                ElementNode.CharSet(nextToken, charSetNodes, closeBracket)
            }
            else -> {
                ElementNode.Empty()
            }
        }
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

    private fun getDefaultToken(): Pair<AntlrToken, Int> {
        var currentToken: AntlrToken
        var currentOffset = 0
        do {
            currentToken = tokenStream.getToken(tokenIndex + currentOffset)
            currentOffset++
        }
        while (currentToken.channel != AntlrTokenChannel.Default)

        return Pair(currentToken, currentOffset)
    }

    private fun matchToken(): AntlrToken {
        return tokenStream.getToken(tokenIndex).also { tokenIndex++ }
    }

    private fun getToken(offset: Int): AntlrToken {
        return tokenStream.getToken(tokenIndex + offset)
    }

    private fun incTokenIndex(offset: Int) {
        tokenIndex += offset
    }
}