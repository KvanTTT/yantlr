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
    data class Handle(val start: State, val end: State, val endTransitions: MutableList<Transition>)

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
        val end = State(emptyList())
        ruleHandle.endTransitions.add(EpsilonTransition(end, emptyList()))
        result[rule] = RuleState(rule, node, ruleHandle.start, end)
        return ruleHandle
    }

    override fun visitBlockNode(node: BlockNode): Handle {
        val startTransitions = mutableListOf<Transition>()
        val endTransitions = mutableListOf<Transition>()
        val end = State(endTransitions)

        fun processAlternative(alternativeNode: AlternativeNode) {
            val altNodeHandle = visitAlternativeNode(alternativeNode)
            startTransitions.add(EpsilonTransition(altNodeHandle.start, listOf(alternativeNode)))
            altNodeHandle.endTransitions.add(EpsilonTransition(end, listOf(node)))
        }

        processAlternative(node.alternativeNode)
        node.orAlternativeNodes.forEach { processAlternative(it.alternativeNode) }

        return Handle(State(startTransitions), end, endTransitions)
    }

    override fun visitAlternativeNode(node: AlternativeNode): Handle {
        var endTransitions = mutableListOf<Transition>()
        val start = State(endTransitions)
        var end = start

        node.elementNodes.forEach {
            val newHandle = visitElementNode(it)
            endTransitions.add(EpsilonTransition(newHandle.start, listOf(it)))
            end = newHandle.end
            endTransitions = newHandle.endTransitions
        }

        return Handle(start, end, endTransitions)
    }

    override fun visitElementNode(node: ElementNode): Handle {
        var endTransitions = mutableListOf<Transition>()
        val startTransitions = endTransitions
        val start = State(endTransitions)
        var end = start

        // TODO: implement greedy processing
        fun ElementSuffixNode?.processElementSuffix() {
            if (this == null) return
            when (ebnf.type) {
                AntlrTokenType.Question -> {
                    startTransitions.add(EpsilonTransition(end, listOf(ebnf)))
                }
                AntlrTokenType.Star -> {
                    startTransitions.add(EpsilonTransition(end, listOf(ebnf)))
                    endTransitions.add(EpsilonTransition(start, listOf(ebnf)))
                }
                AntlrTokenType.Plus -> {
                    endTransitions.add(EpsilonTransition(start, listOf(ebnf)))
                }
                else -> {
                    error("Unexpected token type: ${ebnf.type}")
                }
            }
        }

        when (node) {
            is ElementNode.StringLiteral -> {
                for (charToken in node.chars) {
                    val newTransitions = mutableListOf<Transition>()
                    end = State(newTransitions)
                    val intervalSet = IntervalSet(getCharCode(charToken, stringLiteral = true))
                    endTransitions.add(SetTransition(intervalSet, end, listOf(charToken)))
                    endTransitions = newTransitions
                }
                node.elementSuffix.processElementSuffix()
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
                end = State(newTransitions)
                endTransitions.add(SetTransition(IntervalSet(intervals), end, treeNodes))
                endTransitions = newTransitions
                node.elementSuffix.processElementSuffix()
            }
            is ElementNode.Block -> {
                val blockNodeHandle = visitBlockNode(node.blockNode)
                endTransitions.add(EpsilonTransition(blockNodeHandle.start, listOf(node.blockNode)))
                end = blockNodeHandle.end
                endTransitions = blockNodeHandle.endTransitions
                node.elementSuffix.processElementSuffix()
            }
            else -> TODO("Not yet implemented")
        }

        return Handle(start, end, endTransitions)
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