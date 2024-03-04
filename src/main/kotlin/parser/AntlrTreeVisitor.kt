package parser

abstract class AntlrTreeVisitor {
    abstract fun visitTreeNode(node: AntlrNode)

    abstract fun visitToken(token: AntlrToken)

    open fun visitGrammarNode(node: GrammarNode) = visitTreeNode(node)

    open fun visitGrammarAltNode(node: GrammarNode.AltNode) = visitTreeNode(node)

    open fun visitGrammarAltLexerNode(node: GrammarNode.AltLexerNode) = visitGrammarAltNode(node)

    open fun visitGrammarAltParserNode(node: GrammarNode.AltParserNode) = visitGrammarAltNode(node)

    open fun visitRuleNode(node: RuleNode) = visitTreeNode(node)

    open fun visitRuleAltNode(node: RuleNode.AltNode) = visitTreeNode(node)

    open fun visitRuleAltLexerIdNode(node: RuleNode.AltLexerIdNode) = visitRuleAltNode(node)

    open fun visitRuleAltParserIdNode(node: RuleNode.AltParserIdNode) = visitRuleAltNode(node)

    open fun visitBlockNode(node: BlockNode) = visitTreeNode(node)

    open fun visitBlockOrAlternativeNodes(node: BlockNode.OrAlternativeNode) = visitTreeNode(node)

    open fun visitAlternativeNode(node: AlternativeNode) = visitTreeNode(node)

    open fun visitElementNode(node: ElementNode) = visitTreeNode(node)

    open fun visitElementLexerId(node: ElementNode.ElementLexerId) = visitElementNode(node)

    open fun visitElementParserId(node: ElementNode.ElementParserId) = visitElementNode(node)

    open fun visitElementBlock(node: ElementNode.ElementBlock) = visitElementNode(node)
}
