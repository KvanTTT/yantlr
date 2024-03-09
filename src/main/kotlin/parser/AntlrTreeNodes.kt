package parser

import AntlrTreeVisitor

sealed class AntlrNode {
    val leftToken by lazy { calculateLeftToken() }

    abstract fun calculateLeftToken(): AntlrToken?

    abstract fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R?
}

class GrammarNode(
    val lexerOrParserToken: AntlrToken?,
    val grammarToken: AntlrToken,
    val parserIdToken: AntlrToken,
    val semicolonToken: AntlrToken,
    val ruleNodes: List<RuleNode>,
    val endNode: EndNode?,
) : AntlrNode() {
    override fun calculateLeftToken(): AntlrToken = lexerOrParserToken ?: grammarToken

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        lexerOrParserToken?.let { visitor.visitToken(it) }
        visitor.visitToken(grammarToken)
        visitor.visitToken(parserIdToken)
        visitor.visitToken(semicolonToken)
        ruleNodes.forEach { visitor.visitRuleNode(it) }
        endNode?.let { visitor.visitTreeNode(it) }
        return null
    }
}

class RuleNode(
    val lexerOrParserIdToken: AntlrToken,
    val colonToken: AntlrToken,
    val blockNode: BlockNode,
    val semicolonToken: AntlrToken
) : AntlrNode() {
    override fun calculateLeftToken(): AntlrToken = lexerOrParserIdToken

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        visitor.visitToken(lexerOrParserIdToken)
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
        override fun calculateLeftToken(): AntlrToken = orToken

        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(orToken)
            visitor.visitAlternativeNode(alternativeNode)
            return null
        }
    }

    override fun calculateLeftToken(): AntlrToken? = alternativeNode.calculateLeftToken()

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        visitor.visitAlternativeNode(alternativeNode)
        orAlternativeNodes.forEach { visitor.visitBlockOrAlternativeNodes(it) }
        return null
    }
}

class AlternativeNode(val elementNodes: List<ElementNode>) : AntlrNode() {
    override fun calculateLeftToken(): AntlrToken? = elementNodes.firstOrNull()?.calculateLeftToken()

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        elementNodes.forEach { visitor.visitElementNode(it) }
        return null
    }
}

sealed class ElementNode(val endNode: EndNode?) : AntlrNode() {
    class Empty(endNode: EndNode? = null) : ElementNode(endNode) {
        override fun calculateLeftToken(): AntlrToken? = null

        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? = null
    }

    class LexerId(val lexerId: AntlrToken, val elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(endNode) {
        override fun calculateLeftToken(): AntlrToken = lexerId

        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(lexerId)
            elementSuffix?.let { visitor.visitTreeNode(it) }
            return null
        }
    }

    class ParserId(val parserId: AntlrToken, val elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(endNode) {
        override fun calculateLeftToken(): AntlrToken = parserId

        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(parserId)
            elementSuffix?.let { visitor.visitTreeNode(it) }
            return null
        }
    }

    class Block(val leftParen: AntlrToken, val blockNode: BlockNode, val rightParen: AntlrToken, val elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(endNode) {
        override fun calculateLeftToken(): AntlrToken = leftParen

        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(leftParen)
            visitor.visitBlockNode(blockNode)
            visitor.visitToken(rightParen)
            elementSuffix?.let { visitor.visitTreeNode(it) }
            return null
        }
    }

    class StringLiteral(val openQuote: AntlrToken, val chars: List<AntlrToken>, val closeQuote: AntlrToken, val elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(endNode) {
        override fun calculateLeftToken(): AntlrToken = openQuote

        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(openQuote)
            chars.forEach { visitor.visitToken(it) }
            visitor.visitToken(closeQuote)
            elementSuffix?.let { visitor.visitTreeNode(it) }
            return null
        }
    }

    class CharSet(val openBracket: AntlrToken, val children: List<CharHyphenChar>, val closeBracket: AntlrToken, val elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(endNode) {
        class CharHyphenChar(
            val char: AntlrToken,
            val range: HyphenChar?
        ) : AntlrNode() {
            class HyphenChar(val hyphen: AntlrToken, val char: AntlrToken) : AntlrNode() {
                override fun calculateLeftToken(): AntlrToken = hyphen

                override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
                    visitor.visitToken(hyphen)
                    visitor.visitToken(char)
                    return null
                }
            }

            override fun calculateLeftToken(): AntlrToken = char

            override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
                visitor.visitToken(char)
                range?.let { visitor.visitElementCharSetCharHyphenCharHyphenCharNode(it) }
                return null
            }
        }

        override fun calculateLeftToken(): AntlrToken = openBracket

        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(openBracket)
            children.forEach { visitor.visitElementCharSetCharHyphenCharNode(it) }
            visitor.visitToken(closeBracket)
            elementSuffix?.let { visitor.visitTreeNode(it) }
            return null
        }
    }
}

class ElementSuffixNode(val ebnf: AntlrToken, val nonGreedy: AntlrToken?) : AntlrNode() {
    override fun calculateLeftToken(): AntlrToken = ebnf

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        visitor.visitToken(ebnf)
        nonGreedy?.let { visitor.visitToken(it) }
        return null
    }
}

class EndNode(val extraErrorTokens: List<AntlrToken>, val eofToken: AntlrToken?) : AntlrNode() {
    override fun calculateLeftToken(): AntlrToken? = extraErrorTokens.firstOrNull()?.let { return it }

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        extraErrorTokens.forEach { visitor.visitToken(it) }
        eofToken?.let { visitor.visitToken(it) }
        return null
    }
}