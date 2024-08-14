import parser.*

abstract class AntlrTreeVisitor<out R, in D> {
    abstract fun visitTreeNode(node: AntlrTreeNode, data: D): R

    abstract fun visitToken(token: AntlrToken, data: D): R

    open fun visitGrammarNode(node: GrammarNode, data: D) = visitTreeNode(node, data)

    open fun visitRuleNode(node: RuleNode, data: D) = visitTreeNode(node, data)

    open fun visitCommandsNode(node: CommandsNode, data: D) = visitTreeNode(node, data)

    open fun visitCommaCommandNode(node: CommandsNode.CommaCommandNode, data: D) = visitTreeNode(node, data)

    open fun visitCommandNode(node: CommandNode, data: D) = visitTreeNode(node, data)

    open fun visitModeNode(node: ModeNode, data: D) = visitTreeNode(node, data)

    open fun visitModeDeclaration(node: ModeNode.ModeDeclaration, data: D) = visitTreeNode(node, data)

    open fun visitBlockNode(node: BlockNode, data: D) = visitTreeNode(node, data)

    open fun visitBlockOrAlternativeNodes(node: BlockNode.OrAlternative, data: D) = visitTreeNode(node, data)

    open fun visitAlternativeNode(node: AlternativeNode, data: D) = visitTreeNode(node, data)

    open fun visitElementNode(node: ElementNode, data: D) = visitTreeNode(node, data)

    open fun visitElementBody(node: ElementBody, data: D) = visitTreeNode(node, data)

    open fun visitElementLexerId(node: ElementBody.LexerId, data: D) = visitElementBody(node, data)

    open fun visitElementParserId(node: ElementBody.ParserId, data: D) = visitElementBody(node, data)

    open fun visitElementDot(node: ElementBody.Dot, data: D) = visitElementBody(node, data)

    open fun visitElementBlock(node: ElementBody.Block, data: D) = visitElementBody(node, data)

    open fun visitElementStringLiteralOrRange(node: ElementBody.StringLiteralOrRange, data: D) = visitElementBody(node, data)

    open fun visitElementStringLiteral(node: ElementBody.StringLiteralOrRange.StringLiteral, data: D) = visitTreeNode(node, data)

    open fun visitElementStringLiteralRange(node: ElementBody.StringLiteralOrRange.Range, data: D) = visitTreeNode(node, data)

    open fun visitElementCharSet(node: ElementBody.CharSet, data: D) = visitElementBody(node, data)

    open fun visitElementCharSetCharHyphenCharNode(node: ElementBody.CharSet.CharOrRange, data: D) = visitTreeNode(node, data)

    open fun visitElementCharSetCharHyphenCharHyphenCharNode(node: ElementBody.CharSet.CharOrRange.Range, data: D) = visitTreeNode(node, data)

    open fun visitElementPrefixNode(node: ElementPrefixNode, data: D) = visitTreeNode(node, data)

    open fun visitElementSuffixNode(node: ElementSuffixNode, data: D) = visitTreeNode(node, data)
}
