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
        val token = getToken()
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

        val grammarToken = match(AntlrTokenType.Grammar)

        val idToken = match(if (checkToken(AntlrTokenType.LexerId)) AntlrTokenType.LexerId else AntlrTokenType.ParserId)

        val semicolonToken = match(AntlrTokenType.Semicolon)

        val ruleNodes = mutableListOf<RuleNode>()
        while (!checkToken(AntlrTokenType.EofRule)) {
            ruleNodes.add(parseRule())
        }

        return GrammarNode(
            altNode,
            grammarToken,
            idToken,
            semicolonToken,
            ruleNodes
        )
    }

    // rule
    //   : (LexerId | ParserId) Colon block Semicolon
    //   ;
    fun parseRule(): RuleNode {
        val altNode = when (checkToken(AntlrTokenType.LexerId)) {
            true -> RuleNode.AltLexerIdNode(match(AntlrTokenType.LexerId))
            else -> RuleNode.AltParserIdNode(match(AntlrTokenType.ParserId))
        }

        val colonToken = match(AntlrTokenType.Colon)

        val blockNode = parseBlock()

        val semicolonToken = match(AntlrTokenType.Semicolon)

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
    fun parseBlock(): BlockNode {
        val alternativeNode = parseAlternative()

        val orAlternativeChildren = mutableListOf<BlockNode.OrAlternativeNode>()
        while (checkToken(AntlrTokenType.Or)) {
            orAlternativeChildren.add(BlockNode.OrAlternativeNode(matchToken(), parseAlternative()))
        }

        return BlockNode(alternativeNode, orAlternativeChildren)
    }

    // alternative
    //   : element*
    //   ;
    fun parseAlternative(): AlternativeNode {
        val elementNodes = mutableListOf<ElementNode>()
        while (getToken().type in elementTokenTypes) {
            elementNodes.add(parseElement())
        }

        return AlternativeNode(
            elementNodes
        )
    }

    // element
    //   : LexerId
    //   | ParserId
    //   | '(' block? ')'
    //   ;
    fun parseElement(): ElementNode {
        val nextToken = matchToken()
        return when (nextToken.type) {
            AntlrTokenType.LexerId -> ElementNode.ElementLexerId(nextToken)
            AntlrTokenType.ParserId -> ElementNode.ElementParserId(nextToken)
            else -> ElementNode.ElementBlock(
                nextToken,
                parseBlock(),
                match(AntlrTokenType.RightParen)
            )
        }
    }

    private fun match(tokenType: AntlrTokenType): AntlrToken {
        val currentToken = getToken()
        val realToken = if (currentToken.type == tokenType) {
            nextDefaultToken()
            currentToken
        } else {
            tokenStream.createErrorTokenAtCurrentIndex(tokenType)
        }
        return realToken
    }

    private fun matchToken(): AntlrToken {
        return getToken().also { nextDefaultToken() }
    }

    private fun checkToken(expectedTokenType: AntlrTokenType): Boolean {
        return getToken().type == expectedTokenType
    }

    private fun nextDefaultToken() {
        do {
            tokenIndex++
            val currentToken = getToken()
        } while (currentToken.channel != AntlrTokenChannel.Default)
    }

    private fun getToken(): AntlrToken {
        return tokenStream.getToken(tokenIndex)
    }
}