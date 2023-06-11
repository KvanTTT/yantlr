sealed class AntlrTreeNode(
    val children: List<AntlrTreeNode>,
    val tokenStream: AntlrTokenStream?
) {
    abstract val nodeType: TreeNodeType

    override fun toString() = AntlrTreeNodePrettier.prettify(this)
}

class GrammarTreeNode(
    children: List<AntlrTreeNode>,
    tokenStream: AntlrTokenStream?
) : AntlrTreeNode(children, tokenStream) {
    override val nodeType = TreeNodeType.GrammarRule
}

class RuleTreeNode(
    children: List<AntlrTreeNode>,
    tokenStream: AntlrTokenStream?
) : AntlrTreeNode(children, tokenStream) {
    override val nodeType = TreeNodeType.RuleRule
}

class BlockTreeNode(
    children: List<AntlrTreeNode>,
    tokenStream: AntlrTokenStream?
) : AntlrTreeNode(children, tokenStream) {
    override val nodeType = TreeNodeType.BlockRule
}

class BlockOrAlternativeTreeNode(
    children: List<AntlrTreeNode>,
    tokenStream: AntlrTokenStream?
) : AntlrTreeNode(children, tokenStream) {
    override val nodeType = TreeNodeType.BlockRuleOrAlternative
}

class AlternativeTreeNode(
    children: List<AntlrTreeNode>,
    tokenStream: AntlrTokenStream?
) : AntlrTreeNode(children, tokenStream) {
    override val nodeType = TreeNodeType.AlternativeRule
}

class ElementTreeNode(
    children: List<AntlrTreeNode>,
    tokenStream: AntlrTokenStream?
) : AntlrTreeNode(children, tokenStream) {
    override val nodeType = TreeNodeType.ElementRule
}

class ErrorTokenTreeNode(token: AntlrToken, tokenStream: AntlrTokenStream) : TokenTreeNode(token, tokenStream)

open class TokenTreeNode(val token: AntlrToken, tokenStream: AntlrTokenStream?) : AntlrTreeNode(emptyList(), tokenStream) {
    override val nodeType = TreeNodeType.TokenRule
}

fun createNode(nodeType: TreeNodeType, children: List<AntlrTreeNode>, tokenStream: AntlrTokenStream?): AntlrTreeNode {
    return when (nodeType) {
        TreeNodeType.GrammarRule -> GrammarTreeNode(children, tokenStream)
        TreeNodeType.RuleRule -> RuleTreeNode(children, tokenStream)
        TreeNodeType.BlockRule -> BlockTreeNode(children, tokenStream)
        TreeNodeType.BlockRuleOrAlternative -> BlockOrAlternativeTreeNode(children, tokenStream)
        TreeNodeType.AlternativeRule -> AlternativeTreeNode(children, tokenStream)
        TreeNodeType.ElementRule -> ElementTreeNode(children, tokenStream)
        TreeNodeType.TokenRule -> error("Use createTokenNode instead")
    }
}

fun createTokenNode(token: AntlrToken, tokenStream: AntlrTokenStream?): AntlrTreeNode {
    return TokenTreeNode(token, tokenStream)
}

enum class TreeNodeType {
    GrammarRule,
    RuleRule,
    BlockRule,
    BlockRuleOrAlternative,
    AlternativeRule,
    ElementRule,
    TokenRule
}