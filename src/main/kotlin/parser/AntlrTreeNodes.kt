package parser

import AntlrTreeVisitor

sealed class AntlrNode {
    abstract fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R?
}

class GrammarNode(
    val altNode: AltNode?,
    val grammarToken: AntlrToken,
    val parserIdToken: AntlrToken,
    val semicolonToken: AntlrToken,
    val ruleNodes: List<RuleNode>,
    val eofToken: AntlrToken,
) : AntlrNode() {
    sealed class AltNode : AntlrNode()

    class AltLexerNode(val lexerToken: AntlrToken) : AltNode() {
        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(lexerToken)
            return null
        }
    }

    class AltParserNode(val parserToken: AntlrToken) : AltNode() {
        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(parserToken)
            return null
        }
    }

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        altNode?.let { visitor.visitGrammarAltNode(altNode) }
        visitor.visitToken(grammarToken)
        visitor.visitToken(parserIdToken)
        visitor.visitToken(semicolonToken)
        ruleNodes.forEach { visitor.visitRuleNode(it) }
        visitor.visitToken(eofToken)
        return null
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
        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(lexerIdToken)
            return null
        }
    }

    class AltParserIdNode(val parserIdToken: AntlrToken) : AltNode() {
        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(parserIdToken)
            return null
        }
    }

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        visitor.visitRuleAltNode(altNode)
        visitor.visitToken(colonToken)
        visitor.visitBlockNode(blockNode)
        visitor.visitToken(semicolonToken)
        return null
    }
}

class BlockNode(
    val alternativeNode: AlternativeNode,
    val orAlternativeNodes: List<OrAlternativeNode>
) : AntlrNode() {
    class OrAlternativeNode(val orToken: AntlrToken, val alternativeNode: AlternativeNode) : AntlrNode() {
        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(orToken)
            visitor.visitAlternativeNode(alternativeNode)
            return null
        }
    }

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        visitor.visitAlternativeNode(alternativeNode)
        orAlternativeNodes.forEach { visitor.visitBlockOrAlternativeNodes(it) }
        return null
    }
}

class AlternativeNode(val elementNodes: List<ElementNode>) : AntlrNode() {
    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        elementNodes.forEach { visitor.visitElementNode(it) }
        return null
    }
}

sealed class ElementNode : AntlrNode() {
    class ElementLexerId(val lexerId: AntlrToken) : ElementNode() {
        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(lexerId)
            return null
        }
    }

    class ElementParserId(val parserId: AntlrToken) : ElementNode() {
        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(parserId)
            return null
        }
    }

    class ElementBlock(val leftParen: AntlrToken, val blockNode: BlockNode, val rightParen: AntlrToken) : ElementNode() {
        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(leftParen)
            visitor.visitBlockNode(blockNode)
            visitor.visitToken(rightParen)
            return null
        }
    }
}