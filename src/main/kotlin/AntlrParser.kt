class AntlrParser(val tokenStream: AntlrTokenStream) {
    companion object {
        private val elementTokenTypes = setOf(
            AntlrTokenType.LexerId,
            AntlrTokenType.ParserId,
            AntlrTokenType.LeftParen
        )

        val blockEndTokens = setOf(AntlrTokenType.Semicolon, AntlrTokenType.RightParen)
    }

    var tokenIndex: Int = 0
        private set
        get

    // grammar
    //   : (lexer | parser)? grammar Id Semicolon rule*
    //   ;
    fun parse(): AntlrTreeNode {
        return createTreeNode(TreeNodeType.GrammarRule) {
            val token = getToken()
            if (token.type == AntlrTokenType.Lexer || token.type == AntlrTokenType.Parser) {
                add(TokenTreeNode(token, tokenStream))
                nextDefaultToken()
            }
            add(match(AntlrTokenType.Grammar))

            val grammarIdType = if (checkToken(AntlrTokenType.LexerId))
                AntlrTokenType.LexerId
            else
                AntlrTokenType.ParserId
            add(match(grammarIdType))

            add(match(AntlrTokenType.Semicolon))

            while (!checkToken(AntlrTokenType.EofRule)) {
                add(parseRule())
            }
        }
    }

    // rule
    //   : (LexerId | ParserId) Colon block Semicolon
    //   ;
    fun parseRule(): RuleTreeNode {
        return createTreeNode(TreeNodeType.RuleRule) {
            val ruleId = if (checkToken(AntlrTokenType.LexerId)) AntlrTokenType.LexerId else AntlrTokenType.ParserId
            add(match(ruleId))
            add(match(AntlrTokenType.Colon))
            add(parseBlock())
            add(match(AntlrTokenType.Semicolon))
        }
    }

    // block
    //   : (alternative (OR alternative?)*)*
    //   ;
    fun parseBlock(): BlockTreeNode {
        return createTreeNode(TreeNodeType.BlockRule) {
            if (getToken().type !in blockEndTokens) {
                add(parseAlternative())
                while (checkToken(AntlrTokenType.Or)) {
                    val orAlternativeChildren = mutableListOf<AntlrTreeNode>()
                    orAlternativeChildren.add(match(AntlrTokenType.Or))
                    if (getToken().type !in blockEndTokens) {
                        orAlternativeChildren.add(parseAlternative())
                    }
                    add(createTreeNode(TreeNodeType.BlockRuleOrAlternative) { addAll(orAlternativeChildren) })
                }
            }
        }
    }

    // alternative
    //   : element+
    //   ;
    fun parseAlternative(): AlternativeTreeNode {
        return createTreeNode(TreeNodeType.AlternativeRule) {
            while (getToken().type in elementTokenTypes) {
                add(parseElement())
            }
        }
    }

    // element
    //   : LexerId
    //   | ParserId
    //   | '(' block? ')'
    //   ;
    fun parseElement(): ElementTreeNode {
        return createTreeNode(TreeNodeType.ElementRule) {
            val nextToken = matchToken()
            when (nextToken.type) {
                AntlrTokenType.LexerId,
                AntlrTokenType.ParserId -> add(TokenTreeNode(nextToken, tokenStream))

                AntlrTokenType.LeftParen -> {
                    add(TokenTreeNode(nextToken, tokenStream))
                    add(parseBlock())
                    add(match(AntlrTokenType.RightParen))
                }

                else -> add(ErrorTokenTreeNode(nextToken, tokenStream))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : AntlrTreeNode> createTreeNode(treeNodeType: TreeNodeType, builderAction: MutableList<AntlrTreeNode>.() -> Unit): T {
        return mutableListOf<AntlrTreeNode>().apply(builderAction).let { createNode(treeNodeType, it, tokenStream) as T }
    }

    private fun match(tokenType: AntlrTokenType): TokenTreeNode {
        val currentToken = getToken()
        val realToken = if (currentToken.type == tokenType) {
            nextDefaultToken()
            currentToken
        } else {
            tokenStream.createErrorTokenAtCurrentIndex(tokenType)
        }
        return TokenTreeNode(realToken, tokenStream)
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