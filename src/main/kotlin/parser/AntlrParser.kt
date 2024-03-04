package parser

class AntlrParser(val tokenStream: AntlrTokenStream) {
    companion object {
        private val elementTokenTypes = setOf(
            AntlrTokenType.LexerId,
            AntlrTokenType.ParserId,
            AntlrTokenType.LeftParen
        )
    }

    var tokenIndex: Int = 0
        private set
        get

    // grammar
    //   : (Lexer | Parser)? Grammar ParserId Semicolon rule*
    //   ;
    fun parseGrammar(): GrammarNode {
        val token = nextDefaultToken()
        val altNode = when (token.type) {
            AntlrTokenType.Lexer -> {
                nextDefaultToken()
                GrammarNode.AltLexerNode(token)
            }
            AntlrTokenType.Parser -> {
                nextDefaultToken()
                GrammarNode.AltParserNode(token)
            }
            else -> null
        }

        val grammarToken = if (altNode == null) token else nextDefaultToken()

        val idToken = nextDefaultToken()

        val semicolonToken = nextDefaultToken()

        var nextToken = nextDefaultToken(incIndex = false)
        val ruleNodes = buildList {
            while (nextToken.type != AntlrTokenType.Eof) {
                add(parseRule())
                nextToken = nextDefaultToken(incIndex = false)
            }
        }

        return GrammarNode(altNode, grammarToken, idToken, semicolonToken, ruleNodes, nextToken)
    }

    // rule
    //   : (LexerId | ParserId) Colon block Semicolon
    //   ;
    private fun parseRule(): RuleNode {
        val nextToken = nextDefaultToken()

        val altNode = when (nextToken.type) {
            AntlrTokenType.LexerId -> RuleNode.AltLexerIdNode(nextToken)
            else -> RuleNode.AltParserIdNode(nextToken)
        }

        val colonToken = nextDefaultToken()

        val blockNode = parseBlock()

        val semicolonToken = nextDefaultToken()

        return RuleNode(
            altNode,
            colonToken,
            blockNode,
            semicolonToken
        )
    }

    // block
    //   : alternative (OR alternative)*
    //   ;
    private fun parseBlock(): BlockNode {
        val alternativeNode = parseAlternative()

        val barAlternativeChildren = buildList {
            var nextToken = nextDefaultToken(incIndex = false)
            while (nextToken.type == AntlrTokenType.Bar) {
                incIndex()
                add(BlockNode.OrAlternativeNode(nextToken, parseAlternative()))
                nextToken = nextDefaultToken(incIndex = false)
            }
        }

        return BlockNode(alternativeNode, barAlternativeChildren)
    }

    // alternative
    //   : element*
    //   ;
    private fun parseAlternative(): AlternativeNode {
        val elementNodes = buildList {
            while (nextDefaultToken(incIndex = false).type in elementTokenTypes) {
                add(parseElement())
            }
        }

        return AlternativeNode(elementNodes)
    }

    // element
    //   : LexerId
    //   | ParserId
    //   | '(' block? ')'
    //   ;
    private fun parseElement(): ElementNode {
        val nextToken = nextDefaultToken()
        return when (nextToken.type) {
            AntlrTokenType.LexerId -> ElementNode.ElementLexerId(nextToken)
            AntlrTokenType.ParserId -> ElementNode.ElementParserId(nextToken)
            else -> ElementNode.ElementBlock(
                nextToken,
                parseBlock(),
                nextDefaultToken()
            )
        }
    }

    private fun nextDefaultToken(incIndex: Boolean = true): AntlrToken {
        var currentToken: AntlrToken
        do {
            currentToken = tokenStream.getToken(tokenIndex)
            tokenIndex++
        }
        while (currentToken.channel != AntlrTokenChannel.Default)

        if (!incIndex) {
            tokenIndex--
        }

        return currentToken
    }

    private fun incIndex() {
        tokenIndex++
    }
}