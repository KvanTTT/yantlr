package parser

import AntlrTreeVisitor
import SourceInterval

sealed class AntlrTreeNode : AntlrNode() {
    val leftToken by lazy { calculateLeftToken() }
    val rightToken by lazy { calculateRightToken() }

    abstract fun calculateLeftToken(): AntlrToken

    abstract fun calculateRightToken(): AntlrToken

    override fun getInterval(): SourceInterval {
        return SourceInterval(leftToken.offset, rightToken.end() - leftToken.offset)
    }

    abstract fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R?
}

class GrammarNode(
    val lexerOrParserToken: AntlrToken?,
    val grammarToken: AntlrToken,
    val parserIdToken: AntlrToken,
    val semicolonToken: AntlrToken,
    val ruleNodes: List<RuleNode>,
    val modeNodes: List<ModeNode>,
    val endNode: EndNode?,
) : AntlrTreeNode() {
    override fun calculateLeftToken(): AntlrToken = lexerOrParserToken ?: grammarToken

    override fun calculateRightToken(): AntlrToken =
        endNode?.eofToken ?: modeNodes.lastOrNull()?.rightToken ?: ruleNodes.lastOrNull()?.rightToken ?: semicolonToken

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        lexerOrParserToken?.let { visitor.visitToken(it) }
        visitor.visitToken(grammarToken)
        visitor.visitToken(parserIdToken)
        visitor.visitToken(semicolonToken)
        ruleNodes.forEach { visitor.visitRuleNode(it) }
        modeNodes.forEach { visitor.visitModeNode(it) }
        endNode?.let { visitor.visitTreeNode(it) }
        return null
    }
}

class RuleNode(
    val fragmentToken: AntlrToken?,
    val idToken: AntlrToken,
    val colonToken: AntlrToken,
    val blockNode: BlockNode,
    val semicolonToken: AntlrToken,
) : AntlrTreeNode() {
    override fun calculateLeftToken(): AntlrToken = idToken

    override fun calculateRightToken(): AntlrToken = semicolonToken

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        fragmentToken?.let { visitor.visitToken(it) }
        visitor.visitToken(idToken)
        visitor.visitToken(colonToken)
        visitor.visitBlockNode(blockNode)
        visitor.visitToken(semicolonToken)
        return null
    }
}

class ModeNode(
    val modeToken: AntlrToken,
    val idToken: AntlrToken,
    val semicolonToken: AntlrToken,
    val ruleNodes: List<RuleNode>,
) : AntlrTreeNode() {
    override fun calculateLeftToken(): AntlrToken = modeToken

    override fun calculateRightToken(): AntlrToken = ruleNodes.lastOrNull()?.rightToken ?: semicolonToken

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        visitor.visitToken(modeToken)
        visitor.visitToken(idToken)
        visitor.visitToken(semicolonToken)
        ruleNodes.forEach { visitor.visitRuleNode(it) }
        return null
    }
}

class BlockNode(
    val alternativeNode: AlternativeNode,
    val orAlternativeNodes: List<OrAlternative>
) : AntlrTreeNode() {
    class OrAlternative(val orToken: AntlrToken, val alternativeNode: AlternativeNode) : AntlrTreeNode() {
        override fun calculateLeftToken(): AntlrToken = orToken

        override fun calculateRightToken(): AntlrToken = alternativeNode.rightToken

        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(orToken)
            visitor.visitAlternativeNode(alternativeNode)
            return null
        }
    }

    override fun calculateLeftToken(): AntlrToken = alternativeNode.leftToken

    override fun calculateRightToken(): AntlrToken = orAlternativeNodes.lastOrNull()?.rightToken ?: alternativeNode.rightToken

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        visitor.visitAlternativeNode(alternativeNode)
        orAlternativeNodes.forEach { visitor.visitBlockOrAlternativeNodes(it) }
        return null
    }
}

class AlternativeNode(val elementNodes: List<ElementNode>) : AntlrTreeNode() {
    override fun calculateLeftToken(): AntlrToken = elementNodes.first().leftToken

    override fun calculateRightToken(): AntlrToken = elementNodes.last().rightToken

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        elementNodes.forEach { visitor.visitElementNode(it) }
        return null
    }
}

sealed class ElementNode(val endNode: EndNode?) : AntlrTreeNode() {
    class Empty(val emptyToken: AntlrToken, endNode: EndNode? = null) : ElementNode(endNode) {
        override fun calculateLeftToken(): AntlrToken = emptyToken

        override fun calculateRightToken(): AntlrToken = endNode?.rightToken ?: emptyToken

        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? = null
    }

    class LexerId(val lexerId: AntlrToken, val elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(endNode) {
        override fun calculateLeftToken(): AntlrToken = lexerId

        override fun calculateRightToken(): AntlrToken = endNode?.rightToken ?: elementSuffix?.rightToken ?: lexerId

        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(lexerId)
            elementSuffix?.let { visitor.visitTreeNode(it) }
            return null
        }
    }

    class ParserId(val parserId: AntlrToken, val elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(endNode) {
        override fun calculateLeftToken(): AntlrToken = parserId

        override fun calculateRightToken(): AntlrToken = endNode?.rightToken ?: elementSuffix?.rightToken ?: parserId

        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(parserId)
            elementSuffix?.let { visitor.visitTreeNode(it) }
            return null
        }
    }

    class Block(val leftParen: AntlrToken, val blockNode: BlockNode, val rightParen: AntlrToken, val elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(endNode) {
        override fun calculateLeftToken(): AntlrToken = leftParen

        override fun calculateRightToken(): AntlrToken = endNode?.rightToken ?: elementSuffix?.rightToken ?: rightParen

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

        override fun calculateRightToken(): AntlrToken = endNode?.rightToken ?: elementSuffix?.rightToken ?: closeQuote

        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(openQuote)
            chars.forEach { visitor.visitToken(it) }
            visitor.visitToken(closeQuote)
            elementSuffix?.let { visitor.visitTreeNode(it) }
            return null
        }
    }

    class CharSet(val openBracket: AntlrToken, val children: List<CharOrRange>, val closeBracket: AntlrToken, val elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(endNode) {
        class CharOrRange(
            val char: AntlrToken,
            val range: Range?
        ) : AntlrTreeNode() {
            class Range(val hyphen: AntlrToken, val char: AntlrToken) : AntlrTreeNode() {
                override fun calculateLeftToken(): AntlrToken = hyphen

                override fun calculateRightToken(): AntlrToken = char

                override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
                    visitor.visitToken(hyphen)
                    visitor.visitToken(char)
                    return null
                }
            }

            override fun calculateLeftToken(): AntlrToken = char

            override fun calculateRightToken(): AntlrToken = range?.char ?: char

            override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
                visitor.visitToken(char)
                range?.let { visitor.visitElementCharSetCharHyphenCharHyphenCharNode(it) }
                return null
            }
        }

        override fun calculateLeftToken(): AntlrToken = openBracket

        override fun calculateRightToken(): AntlrToken = endNode?.rightToken ?: elementSuffix?.rightToken ?: closeBracket

        override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
            visitor.visitToken(openBracket)
            children.forEach { visitor.visitElementCharSetCharHyphenCharNode(it) }
            visitor.visitToken(closeBracket)
            elementSuffix?.let { visitor.visitTreeNode(it) }
            return null
        }
    }
}

class ElementSuffixNode(val ebnf: AntlrToken, val nonGreedy: AntlrToken?) : AntlrTreeNode() {
    override fun calculateLeftToken(): AntlrToken = ebnf

    override fun calculateRightToken(): AntlrToken = nonGreedy ?: ebnf

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        visitor.visitToken(ebnf)
        nonGreedy?.let { visitor.visitToken(it) }
        return null
    }
}

class EndNode(val extraErrorTokens: List<AntlrToken>, val eofToken: AntlrToken) : AntlrTreeNode() {
    override fun calculateLeftToken(): AntlrToken = extraErrorTokens.firstOrNull()?.let { return it } ?: eofToken

    override fun calculateRightToken(): AntlrToken = eofToken

    override fun <R> acceptChildren(visitor: AntlrTreeVisitor<R>): R? {
        extraErrorTokens.forEach { visitor.visitToken(it) }
        eofToken?.let { visitor.visitToken(it) }
        return null
    }
}