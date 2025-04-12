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
    val commandsNode: CommandsNode?,
    val semicolonToken: AntlrToken,
) : AntlrTreeNode() {
    override fun calculateLeftToken(): AntlrToken = idToken

    override fun calculateRightToken(): AntlrToken = semicolonToken

    override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
        fragmentToken?.let { visitor.visitToken(it, data) }
        visitor.visitToken(idToken, data)
        visitor.visitToken(colonToken, data)
        visitor.visitBlockNode(blockNode, data)
        commandsNode?.let { visitor.visitCommandsNode(it, data) }
        visitor.visitToken(semicolonToken, data)
        return null
    }
}

class CommandsNode(
    val arrowToken: AntlrToken,
    val commandNode: CommandNode,
    val commaCommandNodes: List<CommaCommandNode>,
) : AntlrTreeNode() {
    class CommaCommandNode(val comma: AntlrToken, val command: CommandNode) : AntlrTreeNode() {
        override fun calculateLeftToken(): AntlrToken = comma

        override fun calculateRightToken(): AntlrToken = command.rightToken

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            visitor.visitToken(comma, data)
            visitor.visitCommandNode(command, data)
            return null
        }
    }

    override fun calculateLeftToken(): AntlrToken = arrowToken

    override fun calculateRightToken(): AntlrToken = commaCommandNodes.lastOrNull()?.rightToken ?: commandNode.rightToken

    override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
        visitor.visitToken(arrowToken, data)
        visitor.visitTreeNode(commandNode, data)
        commaCommandNodes.forEach { visitor.visitCommaCommandNode(it, data) }
        return null
    }
}

class CommandNode(
    val nameToken: AntlrToken,
    val paramsNode: Params?,
) : AntlrTreeNode() {
    class Params(val leftParenToken: AntlrToken, val paramToken: AntlrToken, val rightParenToken: AntlrToken) : AntlrTreeNode() {
        override fun calculateLeftToken(): AntlrToken = leftParenToken

        override fun calculateRightToken(): AntlrToken = rightParenToken

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            visitor.visitToken(leftParenToken, data)
            visitor.visitToken(paramToken, data)
            visitor.visitToken(rightParenToken, data)
            return null
        }
    }

    override fun calculateLeftToken(): AntlrToken = nameToken

    override fun calculateRightToken(): AntlrToken = paramsNode?.rightParenToken ?: nameToken

    override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
        visitor.visitToken(nameToken, data)
        paramsNode?.let { visitor.visitTreeNode(it, data) }
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

class ElementNode(
    val elementPrefix: ElementPrefixNode?,
    val elementBody: ElementBody,
    val elementSuffix: ElementSuffixNode?,
) : AntlrTreeNode() {
    override fun calculateLeftToken(): AntlrToken = elementPrefix?.leftToken ?: elementBody.leftToken

    override fun calculateRightToken(): AntlrToken = elementSuffix?.rightToken ?: elementBody.rightToken

    override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
        elementPrefix?.let { visitor.visitElementPrefixNode(it, data) }
        visitor.visitElementBody(elementBody, data)
        elementSuffix?.let { visitor.visitElementSuffixNode(it, data) }
        return null
    }
}

class ElementPrefixNode(val label: AntlrToken, val equalToken: AntlrToken) : AntlrTreeNode() {
    override fun calculateLeftToken(): AntlrToken = label

    override fun calculateRightToken(): AntlrToken = equalToken

    override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
        visitor.visitToken(label, data)
        visitor.visitToken(equalToken, data)
        return null
    }
}

sealed class ElementBody(val tilde: AntlrToken?) : AntlrTreeNode() {
    class Empty(tilde: AntlrToken?, val emptyToken: AntlrToken) : ElementBody(tilde) {
        override fun calculateLeftToken(): AntlrToken = tilde ?: emptyToken

        override fun calculateRightToken(): AntlrToken = emptyToken

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            tilde?.let { visitor.visitToken(it, data) }
            visitor.visitToken(emptyToken, data)
            return null
        }
    }

    class LexerId(tilde: AntlrToken?, val lexerId: AntlrToken) : ElementBody(tilde) {
        override fun calculateLeftToken(): AntlrToken = tilde ?: lexerId

        override fun calculateRightToken(): AntlrToken = lexerId

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            tilde?.let { visitor.visitToken(it, data) }
            visitor.visitToken(lexerId, data)
            return null
        }
    }

    class ParserId(tilde: AntlrToken?, val parserId: AntlrToken) : ElementBody(tilde) {
        override fun calculateLeftToken(): AntlrToken = tilde ?: parserId

        override fun calculateRightToken(): AntlrToken = parserId

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            tilde?.let { visitor.visitToken(it, data) }
            visitor.visitToken(parserId, data)
            return null
        }
    }

    class Dot(tilde: AntlrToken?, val dotToken: AntlrToken) : ElementBody(tilde) {
        override fun calculateLeftToken(): AntlrToken = tilde ?: dotToken

        override fun calculateRightToken(): AntlrToken = dotToken

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            tilde?.let { visitor.visitToken(it, data) }
            visitor.visitToken(dotToken, data)
            return null
        }
    }

    class Block(
        tilde: AntlrToken?,
        val leftParen: AntlrToken,
        val blockNode: BlockNode,
        val rightParen: AntlrToken,
    ) : ElementBody(tilde) {
        override fun calculateLeftToken(): AntlrToken = tilde ?: leftParen

        override fun calculateRightToken(): AntlrToken = rightParen

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            tilde?.let { visitor.visitToken(it, data) }
            visitor.visitToken(leftParen, data)
            visitor.visitBlockNode(blockNode, data)
            visitor.visitToken(rightParen, data)
            return null
        }
    }

    class StringLiteralOrRange(
        tilde: AntlrToken?,
        val stringLiteral: StringLiteral,
        val range: Range?,
    ) : ElementBody(tilde) {
        class Range(val rangeToken: AntlrToken, val stringLiteral: StringLiteral) : AntlrTreeNode() {
            override fun calculateLeftToken(): AntlrToken = rangeToken

            override fun calculateRightToken(): AntlrToken = stringLiteral.rightToken

            override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
                visitor.visitToken(rangeToken, data)
                visitor.visitTreeNode(stringLiteral, data)
                return null
            }
        }

        class StringLiteral(val openQuote: AntlrToken, val chars: List<AntlrToken>, val closeQuote: AntlrToken) :
            AntlrTreeNode() {
            override fun calculateLeftToken(): AntlrToken = openQuote

            override fun calculateRightToken(): AntlrToken = closeQuote

            override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
                visitor.visitToken(openQuote, data)
                chars.forEach { visitor.visitToken(it, data) }
                visitor.visitToken(closeQuote, data)
                return null
            }
        }

        override fun calculateLeftToken(): AntlrToken = tilde ?: stringLiteral.leftToken

        override fun calculateRightToken(): AntlrToken =
            range?.rightToken ?: stringLiteral.rightToken

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            tilde?.let { visitor.visitToken(it, data) }
            visitor.visitElementStringLiteral(stringLiteral, data)
            range?.let { visitor.visitElementStringLiteralRange(it, data) }
            return null
        }
    }

    class CharSet(
        tilde: AntlrToken?,
        val openBracket: AntlrToken,
        val children: List<CharOrRange>,
        val closeBracket: AntlrToken,
    ) : ElementBody(tilde) {
        class CharOrRange(val char: AntlrToken, val range: Range?) : AntlrTreeNode() {
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

        override fun calculateLeftToken(): AntlrToken = tilde ?: openBracket

        override fun calculateRightToken(): AntlrToken = closeBracket

        override fun <R, D> acceptChildren(visitor: AntlrTreeVisitor<R, D>, data: D): R? {
            tilde?.let { visitor.visitToken(it, data) }
            visitor.visitToken(openBracket, data)
            children.forEach { visitor.visitElementCharSetCharHyphenCharNode(it, data) }
            visitor.visitToken(closeBracket, data)
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