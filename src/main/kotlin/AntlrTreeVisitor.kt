import parser.*

abstract class AntlrTreeVisitor<out R, in D> {
    abstract fun visitTreeNode(node: AntlrTreeNode, data: D): R

    abstract fun visitToken(token: AntlrToken, data: D): R

    open fun visitGrammarNode(node: GrammarNode, data: D) = visitTreeNode(node, data)

    open fun visitRuleNode(node: RuleNode, data: D) = visitTreeNode(node, data)

    open fun visitModeNode(node: ModeNode, data: D) = visitTreeNode(node, data)

    open fun visitModeDeclaration(node: ModeNode.ModeDeclaration, data: D) = visitTreeNode(node, data)

    open fun visitBlockNode(node: BlockNode, data: D) = visitTreeNode(node, data)

    open fun visitBlockOrAlternativeNodes(node: BlockNode.OrAlternative, data: D) = visitTreeNode(node, data)

    open fun visitAlternativeNode(node: AlternativeNode, data: D) = visitTreeNode(node, data)

    open fun visitElementNode(node: ElementNode, data: D) = visitTreeNode(node, data)

    open fun visitElementLexerId(node: ElementNode.LexerId, data: D) = visitElementNode(node, data)

    open fun visitElementParserId(node: ElementNode.ParserId, data: D) = visitElementNode(node, data)

    open fun visitElementDot(node: ElementNode.Dot, data: D) = visitElementNode(node, data)

    open fun visitElementBlock(node: ElementNode.Block, data: D) = visitElementNode(node, data)

    open fun visitElementStringLiteralOrRange(node: ElementNode.StringLiteralOrRange, data: D) = visitElementNode(node, data)

    open fun visitElementStringLiteralRange(node: ElementNode.StringLiteralOrRange.Range, data: D) = visitTreeNode(node, data)

    open fun visitElementCharSet(node: ElementNode.CharSet, data: D) = visitElementNode(node, data)

    open fun visitElementCharSetCharHyphenCharNode(node: ElementNode.CharSet.CharOrRange, data: D) = visitTreeNode(node, data)

    open fun visitElementCharSetCharHyphenCharHyphenCharNode(node: ElementNode.CharSet.CharOrRange.Range, data: D) = visitTreeNode(node, data)

    open fun visitElementSuffixNode(node: ElementSuffixNode, data: D) = visitTreeNode(node, data)
}
