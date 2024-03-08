import parser.*

abstract class AntlrTreeVisitor<out R> {
    abstract fun visitTreeNode(node: AntlrNode): R

    abstract fun visitToken(token: AntlrToken): R

    open fun visitGrammarNode(node: GrammarNode) = visitTreeNode(node)

    open fun visitRuleNode(node: RuleNode) = visitTreeNode(node)

    open fun visitBlockNode(node: BlockNode) = visitTreeNode(node)

    open fun visitBlockOrAlternativeNodes(node: BlockNode.OrAlternativeNode) = visitTreeNode(node)

    open fun visitAlternativeNode(node: AlternativeNode) = visitTreeNode(node)

    open fun visitElementNode(node: ElementNode) = visitTreeNode(node)

    open fun visitElementLexerId(node: ElementNode.LexerId) = visitElementNode(node)

    open fun visitElementParserId(node: ElementNode.ParserId) = visitElementNode(node)

    open fun visitElementBlock(node: ElementNode.Block) = visitElementNode(node)

    open fun visitElementStringLiteral(node: ElementNode.StringLiteral) = visitElementNode(node)

    open fun visitElementCharSet(node: ElementNode.CharSet) = visitElementNode(node)

    open fun visitElementCharSetCharHyphenCharNode(node: ElementNode.CharSet.CharHyphenChar) = visitTreeNode(node)

    open fun visitElementCharSetCharHyphenCharHyphenCharNode(node: ElementNode.CharSet.CharHyphenChar.HyphenChar) = visitTreeNode(node)
}
