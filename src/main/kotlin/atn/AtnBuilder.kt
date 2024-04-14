package atn

import AntlrTreeVisitor
import SemanticsDiagnostics
import parser.*
import semantics.Rule
import java.util.LinkedHashMap

class AtnBuilder(
    val lexer: AntlrLexer,
    private val rules: Map<String, Rule>,
    private val diagnosticReporter: ((SemanticsDiagnostics) -> Unit)? = null,
) : AntlrTreeVisitor<AtnBuilder.Handle?>() {
    data class Handle(val start: State, val endTransitions: MutableList<Transition>)

    private val result = LinkedHashMap<Rule, RuleState>()

    fun build(root: GrammarNode): Atn {
        visitTreeNode(root)
        return Atn(result)
    }

    override fun visitTreeNode(node: AntlrTreeNode): Handle? {
        return node.acceptChildren(this)
    }

    override fun visitToken(token: AntlrToken): Handle? {
        return null
    }

    override fun visitRuleNode(node: RuleNode): Handle {
        val ruleHandle = visitBlockNode(node.blockNode)
        val rule = rules.getValue(node.lexerOrParserIdToken.value!!)
        val endState = State(emptyList())
        ruleHandle.endTransitions.add(EpsilonTransition(endState, emptyList()))
        result[rule] = RuleState(rule, node, ruleHandle.start, endState)
        return ruleHandle
    }

    override fun visitBlockNode(node: BlockNode): Handle {
        val startTransitions = mutableListOf<Transition>()
        val endTransitions = mutableListOf<Transition>()
        val endState = State(endTransitions)

        fun processAlternative(alternativeNode: AlternativeNode) {
            val altNodeHandle = visitAlternativeNode(alternativeNode)
            startTransitions.add(EpsilonTransition(altNodeHandle.start, listOf(alternativeNode)))
            altNodeHandle.endTransitions.add(EpsilonTransition(endState, listOf(node)))
        }

        processAlternative(node.alternativeNode)
        node.orAlternativeNodes.forEach { processAlternative(it.alternativeNode) }

        return Handle(State(startTransitions), endTransitions)
    }

    override fun visitAlternativeNode(node: AlternativeNode): Handle {
        var transitions = mutableListOf<Transition>()
        val start = State(transitions)

        node.elementNodes.forEach {
            val newHandle = visitElementNode(it)
            transitions.add(EpsilonTransition(newHandle.start, listOf(it)))
            transitions = newHandle.endTransitions
        }

        return Handle(start, transitions)
    }

    override fun visitElementNode(node: ElementNode): Handle {
        var transitions = mutableListOf<Transition>()
        val start = State(transitions)

        when (node) {
            is ElementNode.StringLiteral -> {
                for (char in node.chars) {
                    val value = char.value!!
                    val code = when (char.type) {
                        AntlrTokenType.Char -> value[0].code
                        AntlrTokenType.EscapedChar -> value[1].let { antlrLiteralToEscapeChars[it] ?: it }.code
                        AntlrTokenType.UnicodeEscapedChar -> value.substring(2).toInt(16)
                        else -> error("Unexpected token type: ${char.type}") // TODO: handle error tokens?
                    }
                    val newTransitions = mutableListOf<Transition>()
                    transitions.add(SetTransition(IntervalSet(code), State(newTransitions), listOf(char)))
                    transitions = newTransitions
                }
            }
            else -> TODO("Not yet implemented")
        }

        return Handle(start, transitions)
    }
}