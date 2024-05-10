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
        return DeclarationCollectorVisitor().collect(grammarNode)
    }

    private inner class DeclarationCollectorVisitor : AntlrTreeVisitor<Unit>() {
        private var currentModeLexerRules: LinkedHashMap<String, Rule> = linkedMapOf()
        private var lexerModes: LinkedHashMap<String, Mode> = linkedMapOf(
            DEFAULT_MODE_NAME to Mode(modeTreeNode = null, currentModeLexerRules)
        )
        private val lexerRules: LinkedHashMap<String, Rule> = linkedMapOf()
        private val parserRules: LinkedHashMap<String, Rule> = linkedMapOf<String, Rule>()

        fun collect(grammarNode: GrammarNode): DeclarationsInfo {
            grammarNode.acceptChildren(this)
            return DeclarationsInfo(lexerModes, lexerRules, parserRules)
        }

        override fun visitTreeNode(node: AntlrTreeNode) {
            node.acceptChildren(this)
        }

        override fun visitToken(token: AntlrToken) {}

        override fun visitRuleNode(node: RuleNode) {
            val isLexerRule = node.idToken.type == AntlrTokenType.LexerId
            val checkRules = if (isLexerRule) lexerRules else parserRules

            val id = lexer.getTokenValue(node.idToken)
            val existingRule = checkRules[id]
            if (existingRule != null) {
                diagnosticReporter?.invoke(RuleRedefinition(existingRule, node))
            } else {
                val rule = Rule(isLexerRule, references = emptyList(), node)
                if (isLexerRule) {
                    currentModeLexerRules[id] = rule
                    lexerRules[id] = rule
                } else {
                    parserRules[id] = rule
                }
            }
        }

        override fun visitModeNode(node: ModeNode) {
            val id: String = lexer.getTokenValue(node.idToken)
            val existingMode = lexerModes[id]
            if (existingMode != null) {
                currentModeLexerRules = existingMode.rules as LinkedHashMap<String, Rule>
            } else {
                currentModeLexerRules = linkedMapOf()
                lexerModes[id] = Mode(node, currentModeLexerRules)
            }

            node.ruleNodes.forEach { visitRuleNode(it) }
        }
    }
}

class DeclarationsInfo(
    val lexerModes: Map<String, Mode>,
    val lexerRules: Map<String, Rule>,
    val parserRules: Map<String, Rule>,
)