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
            AntlrTokenType.Fragment,
        )

        private val elementSuffixTokenTypes = setOf(
            AntlrTokenType.Question,
            AntlrTokenType.Star,
            AntlrTokenType.Plus,
        )

        private val elementTokenTypes = setOf(
            AntlrTokenType.LexerId,
            AntlrTokenType.ParserId,
            AntlrTokenType.LeftParen,
            AntlrTokenType.Quote,
            AntlrTokenType.LeftBracket,
            AntlrTokenType.Dot,
        ) + elementSuffixTokenTypes

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
    //   : (Lexer | Parser)? Grammar id ';' modes*
    //   ;
    fun parseGrammar(matchToEof: Boolean = true): GrammarNode {
        val lexerOrParserToken = when (getToken().type) {
            AntlrTokenType.Lexer, AntlrTokenType.Parser -> matchToken()
            else -> null
        }

        val grammarToken = matchToken(AntlrTokenType.Grammar)

        val idToken = parseId()

        val semicolonToken = matchToken(AntlrTokenType.Semicolon)

        val modeNodes = buildList {
            var nextToken = getToken()
            while (nextToken.type.let { it == AntlrTokenType.Mode || ruleTokenTypes.contains(it) }) {
                add(parseMode())
                nextToken = getToken()
            }
        }

        val endToken = emitEndNode(emptyList(), matchToEof)

        return GrammarNode(lexerOrParserToken, grammarToken, idToken, semicolonToken, modeNodes, endToken)
    }

    // mode
    //   : 'mode' id ';' rule* | rule+
    //   ;
    private fun parseMode(): ModeNode {
        val modeDeclaration = if (getToken().type == AntlrTokenType.Mode) {
            ModeNode.ModeDeclaration(
                matchToken(AntlrTokenType.Mode),
                parseId(),
                matchToken(AntlrTokenType.Semicolon)
            )
        } else {
            null
        }

        val ruleNodes = buildList {
            var nextToken = getToken()
            while (nextToken.type in ruleTokenTypes) {
                add(parseRule())
                nextToken = getToken()
            }
        }

        return ModeNode(modeDeclaration, ruleNodes)
    }

    // rule
    //   : 'fragment'? id ':' block ';'
    //   ;
    fun parseRule(): RuleNode {
        val fragmentToken = if (getToken().type == AntlrTokenType.Fragment) matchToken() else null

        val lexerIdOrParserIdToken = parseId()

        val colonToken = matchToken(AntlrTokenType.Colon)

        val blockNode = parseBlock(colonToken.end())

        val semicolonToken = matchToken(AntlrTokenType.Semicolon)

        return RuleNode(
            fragmentToken,
            lexerIdOrParserIdToken,
            colonToken,
            blockNode,
            semicolonToken
        )
    }

    // block
    //   : alternative ('|' alternative)*
    //   ;
    private fun parseBlock(lastTokenEnd: Int): BlockNode {
        val alternativeNode = parseAlternative(lastTokenEnd)

        val barAlternativeChildren = buildList {
            var nextToken = getToken()
            while (nextToken.type == AntlrTokenType.Bar) {
                val barToken = matchToken()
                add(BlockNode.OrAlternative(nextToken, parseAlternative(barToken.end())))
                nextToken = getToken()
            }
        }

        return BlockNode(alternativeNode, barAlternativeChildren)
    }

    // alternative
    //   : element+
    //   ;
    fun parseAlternative(lastTokenEnd: Int = 0): AlternativeNode {
        val elementNodes = buildList {
            add(parseElement(false, lastTokenEnd))
            while (getToken().type in elementTokenTypes) {
                add(parseElement(false, lastTokenEnd))
            }
        }

        return AlternativeNode(elementNodes)
    }

    // element
    //   : ( LexerId
    //     | ParserId
    //     | '(' block? ')'
    //     | '\'' char* '\'' range=('..' '\'' char* '\'')?
    //     | '[' (char range=('-' char)?)* ']')
    //     | '.'
    //     |
    //     ) elementSuffix?
    //   ;
    //
    // char
    //   : Char | EscapedChar | UnicodeEscapedChar
    //   ;
    fun parseElement(matchToEof: Boolean = false, lastTokenEnd: Int = 0): ElementNode {
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
            AntlrTokenType.Dot -> {
                ElementNode.Dot(matchToken(), tryParseElementSuffix(), emitEndNode(extraTokens, matchToEof))
            }
            AntlrTokenType.LeftParen -> ElementNode.Block(
                matchToken(),
                parseBlock(nextToken.end()),
                matchToken(AntlrTokenType.RightParen),
                tryParseElementSuffix(),
                emitEndNode(extraTokens, matchToEof),
            )
            AntlrTokenType.Quote -> {
                fun matchStringLiteral(): ElementNode.StringLiteralOrRange.StringLiteral {
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

                    return ElementNode.StringLiteralOrRange.StringLiteral(openQuote, charTokens, closeQuote)
                }

                ElementNode.StringLiteralOrRange(
                    matchStringLiteral(),
                    if (getToken().type == AntlrTokenType.Range) {
                        ElementNode.StringLiteralOrRange.Range(
                            matchToken(),
                            matchStringLiteral(),
                        )
                    } else {
                        null
                    },
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
                                    add(ElementNode.CharSet.CharOrRange(
                                        matchToken(),
                                        ElementNode.CharSet.CharOrRange.Range(
                                            matchToken(),
                                            matchToken(),
                                        )
                                    ))
                                } else {
                                    add(ElementNode.CharSet.CharOrRange(matchToken(), null))
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
                ElementNode.Empty(
                    AntlrToken(AntlrTokenType.Empty, lastTokenEnd, 0),
                    tryParseElementSuffix(),
                    emitEndNode(extraTokens, matchToEof)
                )
            }
        }
    }

    // elementSuffix
    //   : ('?' | '*' | '+') '?'?
    //   ;
    private fun parseElementSuffix(): ElementSuffixNode {
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

    // id
    //   : LexerId
    //   | ParserId
    //   ;
    private fun parseId(): AntlrToken {
        return when (getToken().type) {
            AntlrTokenType.LexerId, AntlrTokenType.ParserId -> matchToken()
            else -> emitMissingToken(tokenType = null)
        }
    }

    private fun emitEndNode(extraTokens: List<AntlrToken>, matchToEof: Boolean): EndNode? {
        for (extraToken in extraTokens) {
            diagnosticReporter?.invoke(ExtraToken(extraToken.getInterval()))
        }

        if (!matchToEof) {
            return extraTokens.takeIf { it.isNotEmpty() }?.let { EndNode(it, AntlrToken(AntlrTokenType.Empty, it.last().end(), 0)) }
        }

        val errorTokens = extraTokens + buildList {
            var nextToken = getToken()
            while (nextToken.type != AntlrTokenType.Eof) {
                diagnosticReporter?.invoke(ExtraToken(nextToken.getInterval()))
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
            .also { diagnosticReporter?.invoke(MissingToken(it.getInterval())) }
    }
}