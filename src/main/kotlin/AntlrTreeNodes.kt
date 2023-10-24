sealed class AntlrNode {
    abstract fun acceptChildren(visitor: AntlrTreeVisitor)
}

class GrammarNode(
    val altNode: AltNode?,
    val grammarToken: AntlrToken,
    val parserIdToken: AntlrToken,
    val semicolonToken: AntlrToken,
    val ruleNodes: List<RuleNode>,
) : AntlrNode() {
    sealed class AltNode : AntlrNode()

    class AltLexerNode(val lexerToken: AntlrToken) : AltNode() {
        override fun acceptChildren(visitor: AntlrTreeVisitor) = visitor.visitToken(lexerToken)
    }

    class AltParserNode(val parserToken: AntlrToken) : AltNode() {
        override fun acceptChildren(visitor: AntlrTreeVisitor) = visitor.visitToken(parserToken)
    }

    override fun acceptChildren(visitor: AntlrTreeVisitor) {
        altNode?.let { visitor.visitGrammarAltNode(altNode) }
        visitor.visitToken(grammarToken)
        visitor.visitToken(parserIdToken)
        visitor.visitToken(semicolonToken)
        ruleNodes.forEach { visitor.visitRuleNode(it) }
    }
}

class RuleNode(
    val altNode: AltNode,
    val colonToken: AntlrToken,
    val blockNode: BlockNode,
    val semicolonToken: AntlrToken
) : AntlrNode() {
    sealed class AltNode : AntlrNode()

    class AltLexerIdNode(val lexerIdToken: AntlrToken) : AltNode() {
        override fun acceptChildren(visitor: AntlrTreeVisitor) = visitor.visitToken(lexerIdToken)
    }

    class AltParserIdNode(val parserIdToken: AntlrToken) : AltNode() {
        override fun acceptChildren(visitor: AntlrTreeVisitor) = visitor.visitToken(parserIdToken)
    }

    override fun acceptChildren(visitor: AntlrTreeVisitor) {
        visitor.visitRuleAltNode(altNode)
        visitor.visitToken(colonToken)
        visitor.visitBlockNode(blockNode)
        visitor.visitToken(semicolonToken)
    }
}

class BlockNode(
    val alternativeNode: AlternativeNode,
    val orAlternativeNodes: List<OrAlternativeNode>
) : AntlrNode() {
    class OrAlternativeNode(val orToken: AntlrToken, val alternativeNode: AlternativeNode) : AntlrNode() {
        override fun acceptChildren(visitor: AntlrTreeVisitor) {
            visitor.visitToken(orToken)
            visitor.visitAlternativeNode(alternativeNode)
        }
    }

    override fun acceptChildren(visitor: AntlrTreeVisitor) {
        visitor.visitAlternativeNode(alternativeNode)
        orAlternativeNodes.forEach { visitor.visitBlockOrAlternativeNodes(it) }
    }
}

class AlternativeNode(val elementNodes: List<ElementNode>) : AntlrNode() {
    override fun acceptChildren(visitor: AntlrTreeVisitor) {
        elementNodes.forEach { visitor.visitElementNode(it) }
    }
}

sealed class ElementNode : AntlrNode() {
    class ElementLexerId(val lexerId: AntlrToken) : ElementNode() {
        override fun acceptChildren(visitor: AntlrTreeVisitor) = visitor.visitToken(lexerId)
    }

    class ElementParserId(val parserId: AntlrToken) : ElementNode() {
        override fun acceptChildren(visitor: AntlrTreeVisitor) = visitor.visitToken(parserId)
    }

    class ElementBlock(val leftParen: AntlrToken, val blockNode: BlockNode, val rightParen: AntlrToken) : ElementNode() {
        override fun acceptChildren(visitor: AntlrTreeVisitor) {
            visitor.visitToken(leftParen)
            visitor.visitBlockNode(blockNode)
            visitor.visitToken(rightParen)
        }
    }
}