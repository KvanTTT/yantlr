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

    abstract fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R?
}

class GrammarNode(
    val lexerOrParserToken: AntlrToken?,
    val grammarToken: AntlrToken,
    val parserIdToken: AntlrToken,
    val semicolonToken: AntlrToken,
    val modeNodes: List<ModeNode>,
    val endNode: EndNode?,
) : AntlrTreeNode() {
    override fun calculateLeftToken(): AntlrToken = lexerOrParserToken ?: grammarToken

    override fun calculateRightToken(): AntlrToken =
        endNode?.eofToken ?: modeNodes.lastOrNull()?.rightToken ?: semicolonToken

    override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
        lexerOrParserToken?.let { visitor.visitToken(it, data) }
        visitor.visitToken(grammarToken, data)
        visitor.visitToken(parserIdToken, data)
        visitor.visitToken(semicolonToken, data)
        modeNodes.forEach { visitor.visitModeNode(it, data) }
        endNode?.let { visitor.visitTreeNode(it, data) }
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

    override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
        fragmentToken?.let { visitor.visitToken(it, data) }
        visitor.visitToken(idToken, data)
        visitor.visitToken(colonToken, data)
        visitor.visitBlockNode(blockNode, data)
        visitor.visitToken(semicolonToken, data)
        return null
    }
}

class ModeNode(val modeDeclaration: ModeDeclaration?, val ruleNodes: List<RuleNode>) : AntlrTreeNode() {
    class ModeDeclaration(val modeToken: AntlrToken, val idToken: AntlrToken, val semicolonToken: AntlrToken) : AntlrTreeNode() {
        override fun calculateLeftToken(): AntlrToken = modeToken

        override fun calculateRightToken(): AntlrToken = semicolonToken

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            visitor.visitToken(modeToken, data)
            visitor.visitToken(idToken, data)
            visitor.visitToken(semicolonToken, data)
            return null
        }
    }

    override fun calculateLeftToken(): AntlrToken = modeDeclaration?.leftToken ?: ruleNodes.first().leftToken

    override fun calculateRightToken(): AntlrToken = if (ruleNodes.isNotEmpty()) ruleNodes.last().rightToken else modeDeclaration!!.rightToken

    override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
        modeDeclaration?.let { visitor.visitModeDeclaration(it, data) }
        ruleNodes.forEach { visitor.visitRuleNode(it, data) }
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

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            visitor.visitToken(orToken, data)
            visitor.visitAlternativeNode(alternativeNode, data)
            return null
        }
    }

    override fun calculateLeftToken(): AntlrToken = alternativeNode.leftToken

    override fun calculateRightToken(): AntlrToken = orAlternativeNodes.lastOrNull()?.rightToken ?: alternativeNode.rightToken

    override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
        visitor.visitAlternativeNode(alternativeNode, data)
        orAlternativeNodes.forEach { visitor.visitBlockOrAlternativeNodes(it, data) }
        return null
    }
}

class AlternativeNode(val elementNodes: List<ElementNode>) : AntlrTreeNode() {
    override fun calculateLeftToken(): AntlrToken = elementNodes.first().leftToken

    override fun calculateRightToken(): AntlrToken = elementNodes.last().rightToken

    override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
        elementNodes.forEach { visitor.visitElementNode(it, data) }
        return null
    }
}

sealed class ElementNode(val elementSuffix: ElementSuffixNode?, val endNode: EndNode?) : AntlrTreeNode() {
    class Empty(val emptyToken: AntlrToken, elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(elementSuffix, endNode) {
        override fun calculateLeftToken(): AntlrToken = emptyToken

        override fun calculateRightToken(): AntlrToken = endNode?.rightToken ?: emptyToken

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            elementSuffix?.let { visitor.visitTreeNode(it, data) }
            return null
        }
    }

    class LexerId(val lexerId: AntlrToken, elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(elementSuffix, endNode) {
        override fun calculateLeftToken(): AntlrToken = lexerId

        override fun calculateRightToken(): AntlrToken = endNode?.rightToken ?: elementSuffix?.rightToken ?: lexerId

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            visitor.visitToken(lexerId, data)
            elementSuffix?.let { visitor.visitTreeNode(it, data) }
            return null
        }
    }

    class ParserId(val parserId: AntlrToken, elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(elementSuffix, endNode) {
        override fun calculateLeftToken(): AntlrToken = parserId

        override fun calculateRightToken(): AntlrToken = endNode?.rightToken ?: elementSuffix?.rightToken ?: parserId

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            visitor.visitToken(parserId, data)
            elementSuffix?.let { visitor.visitTreeNode(it, data) }
            return null
        }
    }

    class Dot(val dotToken: AntlrToken, elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(elementSuffix, endNode) {
        override fun calculateLeftToken(): AntlrToken = dotToken

        override fun calculateRightToken(): AntlrToken = endNode?.rightToken ?: elementSuffix?.rightToken ?: dotToken

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            visitor.visitToken(dotToken, data)
            elementSuffix?.let { visitor.visitTreeNode(it, data) }
            return null
        }
    }

    class Block(val leftParen: AntlrToken, val blockNode: BlockNode, val rightParen: AntlrToken, elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(elementSuffix, endNode) {
        override fun calculateLeftToken(): AntlrToken = leftParen

        override fun calculateRightToken(): AntlrToken = endNode?.rightToken ?: elementSuffix?.rightToken ?: rightParen

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            visitor.visitToken(leftParen, data)
            visitor.visitBlockNode(blockNode, data)
            visitor.visitToken(rightParen, data)
            elementSuffix?.let { visitor.visitTreeNode(it, data) }
            return null
        }
    }

    class StringLiteralOrRange(val stringLiteral: StringLiteral, val range: Range?, elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(elementSuffix, endNode) {
        class Range(val rangeToken: AntlrToken, val stringLiteral: StringLiteral) : AntlrTreeNode() {
            override fun calculateLeftToken(): AntlrToken = rangeToken

            override fun calculateRightToken(): AntlrToken = stringLiteral.rightToken

            override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
                visitor.visitToken(rangeToken, data)
                visitor.visitTreeNode(stringLiteral, data)
                return null
            }
        }

        class StringLiteral(val openQuote: AntlrToken, val chars: List<AntlrToken>, val closeQuote: AntlrToken) : AntlrTreeNode() {
            override fun calculateLeftToken(): AntlrToken = openQuote

            override fun calculateRightToken(): AntlrToken = closeQuote

            override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
                visitor.visitToken(openQuote, data)
                chars.forEach { visitor.visitToken(it, data) }
                visitor.visitToken(closeQuote, data)
                return null
            }
        }

        override fun calculateLeftToken(): AntlrToken = stringLiteral.leftToken

        override fun calculateRightToken(): AntlrToken = endNode?.rightToken ?: elementSuffix?.rightToken ?: range?.rightToken ?: stringLiteral.rightToken

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            visitor.visitTreeNode(stringLiteral, data)
            range?.let { visitor.visitElementStringLiteralRange(it, data) }
            elementSuffix?.let { visitor.visitTreeNode(it, data) }
            return null
        }
    }

    class CharSet(val openBracket: AntlrToken, val children: List<CharOrRange>, val closeBracket: AntlrToken, elementSuffix: ElementSuffixNode?, endNode: EndNode? = null) : ElementNode(elementSuffix, endNode) {
        class CharOrRange(
            val char: AntlrToken,
            val range: Range?
        ) : AntlrTreeNode() {
            class Range(val hyphen: AntlrToken, val char: AntlrToken) : AntlrTreeNode() {
                override fun calculateLeftToken(): AntlrToken = hyphen

                override fun calculateRightToken(): AntlrToken = char

                override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
                    visitor.visitToken(hyphen, data)
                    visitor.visitToken(char, data)
                    return null
                }
            }

            override fun calculateLeftToken(): AntlrToken = char

            override fun calculateRightToken(): AntlrToken = range?.char ?: char

            override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
                visitor.visitToken(char, data)
                range?.let { visitor.visitElementCharSetCharHyphenCharHyphenCharNode(it, data) }
                return null
            }
        }

        override fun calculateLeftToken(): AntlrToken = openBracket

        override fun calculateRightToken(): AntlrToken = endNode?.rightToken ?: elementSuffix?.rightToken ?: closeBracket

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            visitor.visitToken(openBracket, data)
            children.forEach { visitor.visitElementCharSetCharHyphenCharNode(it, data) }
            visitor.visitToken(closeBracket, data)
            elementSuffix?.let { visitor.visitTreeNode(it, data) }
            return null
        }
    }
}

class ElementSuffixNode(val ebnf: AntlrToken, val nonGreedy: AntlrToken?) : AntlrTreeNode() {
    override fun calculateLeftToken(): AntlrToken = ebnf

    override fun calculateRightToken(): AntlrToken = nonGreedy ?: ebnf

    override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
        visitor.visitToken(ebnf, data)
        nonGreedy?.let { visitor.visitToken(it, data) }
        return null
    }
}

class EndNode(val extraErrorTokens: List<AntlrToken>, val eofToken: AntlrToken) : AntlrTreeNode() {
    override fun calculateLeftToken(): AntlrToken = extraErrorTokens.firstOrNull()?.let { return it } ?: eofToken

    override fun calculateRightToken(): AntlrToken = eofToken

    override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
        extraErrorTokens.forEach { visitor.visitToken(it, data) }
        eofToken?.let { visitor.visitToken(it, data) }
        return null
    }
}