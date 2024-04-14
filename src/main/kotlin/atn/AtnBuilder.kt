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
        var endTransitions = mutableListOf<Transition>()
        val start = State(endTransitions)

        node.elementNodes.forEach {
            val newHandle = visitElementNode(it)
            endTransitions.add(EpsilonTransition(newHandle.start, listOf(it)))
            endTransitions = newHandle.endTransitions
        }

        return Handle(start, endTransitions)
    }

    override fun visitElementNode(node: ElementNode): Handle {
        var endTransitions = mutableListOf<Transition>()
        val start = State(endTransitions)

        when (node) {
            is ElementNode.StringLiteral -> {
                for (charToken in node.chars) {
                    val newTransitions = mutableListOf<Transition>()
                    val intervalSet = IntervalSet(getCharCode(charToken, stringLiteral = true))
                    endTransitions.add(SetTransition(intervalSet, State(newTransitions), listOf(charToken)))
                    endTransitions = newTransitions
                }
            }
            is ElementNode.CharSet -> {
                val intervals = mutableListOf<Interval>()
                val treeNodes = mutableListOf<AntlrTreeNode>()
                for (child in node.children) {
                    val startChar = getCharCode(child.char, stringLiteral = false)
                    val endChar = if (child.range != null) {
                        getCharCode(child.range.char, stringLiteral = false)
                    } else {
                        startChar
                    }
                    intervals.add(Interval(startChar, endChar))
                    treeNodes.add(child)
                }
                val newTransitions = mutableListOf<Transition>()
                endTransitions.add(SetTransition(IntervalSet(intervals), State(newTransitions), treeNodes))
                endTransitions = newTransitions
            }

            else -> TODO("Not yet implemented")
        }

        return Handle(start, endTransitions)
    }

    private fun getCharCode(charToken: AntlrToken, stringLiteral: Boolean): Int {
        val literalToEscapeChars = if (stringLiteral) antlrStringLiteralToEscapeChars else antlrCharSetLiteralToEscapeChars
        val value = charToken.value!!
        val code = when (charToken.type) {
            AntlrTokenType.Char -> value[0].code
            AntlrTokenType.EscapedChar -> value[1].let { literalToEscapeChars[it] ?: it }.code
            AntlrTokenType.UnicodeEscapedChar -> value.substring(2).toInt(16)
            else -> error("Unexpected token type: ${charToken.type}") // TODO: handle error tokens?
        }
        return code
    }
}