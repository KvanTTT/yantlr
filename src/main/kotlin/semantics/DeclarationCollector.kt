package semantics

import AntlrTreeVisitor
import DEFAULT_MODE_NAME
import RuleRedefinition
import SemanticsDiagnostic
import parser.*

class DeclarationCollector(
    val lexer: AntlrLexer,
    val diagnosticReporter: ((SemanticsDiagnostic) -> Unit)? = null
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
                    id,
                    ruleNode,
                    isLexer,
                    isFragment = ruleNode.fragmentToken != null,
                    isRecursive = recursiveRules.contains(ruleNode),
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
            lexerModes[modeName] = Mode(modeName, mode.treeNode, currentModeRules)
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

    private class ModeInfo(val treeNode: ModeNode, val ruleNodes: MutableList<RuleNode>)

    private inner class DeclarationCollectorVisitor : AntlrTreeVisitor<Unit, Nothing?>() {
        private lateinit var currentRule: RuleNode
        private var currentModeLexerRules: MutableList<RuleNode> = mutableListOf()
        private var lexerModes: LinkedHashMap<String, ModeInfo> = linkedMapOf()
        private val parserRules: MutableList<RuleNode> = mutableListOf()
        private val recursiveRules: MutableSet<RuleNode> = mutableSetOf()

        fun collect(grammarNode: GrammarNode): InternalDeclarationsInfo {
            grammarNode.acceptChildren(this, null)
            return InternalDeclarationsInfo(lexerModes, parserRules, recursiveRules)
        }

        override fun visitTreeNode(node: AntlrTreeNode, data: Nothing?) {
            node.acceptChildren(this, data)
        }

        override fun visitToken(token: AntlrToken, data: Nothing?) {}

        override fun visitRuleNode(node: RuleNode, data: Nothing?) {
            currentRule = node
            val isLexer = node.idToken.type == AntlrTokenType.LexerId
            val rulesList = if (isLexer) { currentModeLexerRules } else { parserRules }
            rulesList.add(node)

            visitBlockNode(node.blockNode, data)
        }

        override fun visitModeNode(node: ModeNode, data: Nothing?) {
            val id = if (node.modeDeclaration != null) {
                lexer.getTokenValue(node.modeDeclaration.idToken)
            } else {
                DEFAULT_MODE_NAME
            }
            val existingMode = lexerModes[id]
            if (existingMode != null) {
                currentModeLexerRules = existingMode.ruleNodes
            } else {
                currentModeLexerRules = mutableListOf()
                lexerModes[id] = ModeInfo(node, currentModeLexerRules)
            }

            node.ruleNodes.forEach { visitRuleNode(it, data) }
        }

        override fun visitElementNode(node: ElementNode, data: Nothing?) {
            when (node) {
                is ElementNode.LexerId,
                is ElementNode.ParserId -> recursiveRules.add(currentRule)
                is ElementNode.Block -> { visitBlockNode(node.blockNode, data) }
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