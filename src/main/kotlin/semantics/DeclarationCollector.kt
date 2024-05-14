package semantics

import AntlrTreeVisitor
import DEFAULT_MODE_NAME
import RuleRedefinition
import SemanticsDiagnostics
import parser.*

class DeclarationCollector(
    val lexer: AntlrLexer,
    val diagnosticReporter: ((SemanticsDiagnostics) -> Unit)? = null
) {
    fun collect(grammarNode: GrammarNode): DeclarationsInfo {
        val (lexerModesInfo, parserRuleNodes, recursiveRules) = DeclarationCollectorVisitor().collect(grammarNode)

        val lexerModes = LinkedHashMap<String, Mode>()
        val lexerRules = LinkedHashMap<String, Rule>()
        val parserRules = LinkedHashMap<String, Rule>()

        fun createRule(ruleNode: RuleNode): Pair<String, Rule>? {
            val isLexer = ruleNode.idToken.type == AntlrTokenType.LexerId
            val checkRules = if (isLexer) lexerRules else parserRules

            val id = lexer.getTokenValue(ruleNode.idToken)
            val existingRule = checkRules[id]
            return if (existingRule != null) {
                diagnosticReporter?.invoke(RuleRedefinition(existingRule, ruleNode))
                null
            } else {
                id to Rule(
                    isLexer,
                    isFragment = ruleNode.fragmentToken != null,
                    isRecursive = recursiveRules.contains(ruleNode),
                    ruleNode
                )
            }
        }

        for ((modeName, mode) in lexerModesInfo) {
            val currentModeRules = LinkedHashMap<String, Rule>()
            for (ruleNode in mode.ruleNodes) {
                val (ruleName, rule) = createRule(ruleNode) ?: continue
                currentModeRules[ruleName] = rule
                lexerRules[ruleName] = rule
            }
            lexerModes[modeName] = Mode(mode.treeNode, currentModeRules)
        }

        for (ruleNode in parserRuleNodes) {
            val (ruleName, rule) = createRule(ruleNode) ?: continue
            parserRules[ruleName] = rule
        }

        return DeclarationsInfo(lexerModes, lexerRules, parserRules)
    }

    private data class InternalDeclarationsInfo(
        val lexerModes: LinkedHashMap<String, ModeInfo>,
        val parserRuleNodes: List<RuleNode>,
        val recursiveRules: Set<RuleNode>,
    )

    private class ModeInfo(val treeNode: ModeNode?, val ruleNodes: MutableList<RuleNode>)

    private inner class DeclarationCollectorVisitor : AntlrTreeVisitor<Unit>() {
        private lateinit var currentRule: RuleNode
        private var currentModeLexerRules: MutableList<RuleNode> = mutableListOf()
        private var lexerModes: LinkedHashMap<String, ModeInfo> = linkedMapOf(
            DEFAULT_MODE_NAME to ModeInfo(null, currentModeLexerRules)
        )
        private val parserRules: MutableList<RuleNode> = mutableListOf()
        private val recursiveRules: MutableSet<RuleNode> = mutableSetOf()

        fun collect(grammarNode: GrammarNode): InternalDeclarationsInfo {
            grammarNode.acceptChildren(this)
            return InternalDeclarationsInfo(lexerModes, parserRules, recursiveRules)
        }

        override fun visitTreeNode(node: AntlrTreeNode) {
            node.acceptChildren(this)
        }

        override fun visitToken(token: AntlrToken) {}

        override fun visitRuleNode(node: RuleNode) {
            currentRule = node
            val isLexer = node.idToken.type == AntlrTokenType.LexerId
            val rulesList = if (isLexer) { currentModeLexerRules } else { parserRules }
            rulesList.add(node)

            visitBlockNode(node.blockNode)
        }

        override fun visitModeNode(node: ModeNode) {
            val id: String = lexer.getTokenValue(node.idToken)
            val existingMode = lexerModes[id]
            if (existingMode != null) {
                currentModeLexerRules = existingMode.ruleNodes
            } else {
                currentModeLexerRules = mutableListOf()
                lexerModes[id] = ModeInfo(node, currentModeLexerRules)
            }

            node.ruleNodes.forEach { visitRuleNode(it) }
        }

        override fun visitElementNode(node: ElementNode) {
            when (node) {
                is ElementNode.LexerId,
                is ElementNode.ParserId -> recursiveRules.add(currentRule)
                is ElementNode.Block -> { visitBlockNode(node.blockNode) }
                else -> {}
            }
        }
    }
}

class DeclarationsInfo(
    val lexerModes: Map<String, Mode>,
    val lexerRules: Map<String, Rule>,
    val parserRules: Map<String, Rule>,
)