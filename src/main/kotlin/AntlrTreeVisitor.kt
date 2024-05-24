import parser.*

abstract class AntlrTreeVisitor<out R> {
    abstract fun visitTreeNode(node: AntlrTreeNode): R

    abstract fun visitToken(token: AntlrToken): R

    open fun visitGrammarNode(node: GrammarNode) = visitTreeNode(node)

    open fun visitRuleNode(node: RuleNode) = visitTreeNode(node)

    open fun visitModeNode(node: ModeNode) = visitTreeNode(node)

    open fun visitModeDeclaration(node: ModeNode.ModeDeclaration) = visitTreeNode(node)

    open fun visitBlockNode(node: BlockNode) = visitTreeNode(node)

    open fun visitBlockOrAlternativeNodes(node: BlockNode.OrAlternative) = visitTreeNode(node)

    open fun visitAlternativeNode(node: AlternativeNode) = visitTreeNode(node)

    open fun visitElementNode(node: ElementNode) = visitTreeNode(node)

    open fun visitElementLexerId(node: ElementNode.LexerId) = visitElementNode(node)

    open fun visitElementParserId(node: ElementNode.ParserId) = visitElementNode(node)

    open fun visitElementBlock(node: ElementNode.Block) = visitElementNode(node)

    open fun visitElementStringLiteral(node: ElementNode.StringLiteral) = visitElementNode(node)

    open fun visitElementCharSet(node: ElementNode.CharSet) = visitElementNode(node)

    open fun visitElementCharSetCharHyphenCharNode(node: ElementNode.CharSet.CharOrRange) = visitTreeNode(node)

    open fun visitElementCharSetCharHyphenCharHyphenCharNode(node: ElementNode.CharSet.CharOrRange.Range) = visitTreeNode(node)

    open fun visitElementSuffixNode(node: ElementSuffixNode) = visitTreeNode(node)
}
