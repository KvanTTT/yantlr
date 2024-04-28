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
    data class Handle(val start: State, val end: State)

    private var stateCounter = 0
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
        val ruleStateNumber = stateCounter++
        val ruleHandle = visitBlockNode(node.blockNode)

        val end = createState()
        bind(ruleHandle.end, end, node)

        val rule = rules.getValue(node.lexerOrParserIdToken.value!!)
        val ruleState = RuleState(rule, node, mutableListOf(), ruleStateNumber)
        bind(ruleState, ruleHandle.start, node)
        result[rule] = ruleState

        return ruleHandle
    }

    override fun visitBlockNode(node: BlockNode): Handle {
        val start = createState()
        val end by lazy(LazyThreadSafetyMode.NONE) { createState() }

        fun processAlternative(alternativeNode: AlternativeNode) {
            val altNodeHandle = visitAlternativeNode(alternativeNode)
            bind(start, altNodeHandle.start, alternativeNode)
            bind(altNodeHandle.end, end, alternativeNode)
        }

        processAlternative(node.alternativeNode)
        node.orAlternativeNodes.forEach { processAlternative(it.alternativeNode) }

        return Handle(start, end)
    }

    override fun visitAlternativeNode(node: AlternativeNode): Handle {
        val start = createState()
        var end = start

        node.elementNodes.forEach {
            val newHandle = visitElementNode(it)
            bind(end, newHandle.start, it)
            end = newHandle.end
        }

        return Handle(start, end)
    }

    override fun visitElementNode(node: ElementNode): Handle {
        val start = createState()
        var end = start

        // TODO: implement greedy processing
        fun ElementSuffixNode?.processElementSuffix() {
            if (this == null) return
            when (ebnf.type) {
                AntlrTokenType.Question -> {
                    bind(start, end, ebnf)
                }
                AntlrTokenType.Star -> {
                    bind(start, end, ebnf)
                    bind(end, start, ebnf)
                }
                AntlrTokenType.Plus -> {
                    bind(end, start, ebnf)
                }
                else -> {
                    error("Unexpected token type: ${ebnf.type}")
                }
            }
        }

        when (node) {
            is ElementNode.StringLiteral -> {
                for (charToken in node.chars) {
                    val state = createState()
                    val intervalSet = IntervalSet(getCharCode(charToken, stringLiteral = true))
                    SetTransition(intervalSet, end, state, listOf(charToken)).bind()
                    end = state
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

                val state = createState()
                SetTransition(IntervalSet(intervals), end, state, treeNodes).bind()
                end = state

                node.elementSuffix.processElementSuffix()
            }
            is ElementNode.Block -> {
                val blockNodeHandle = visitBlockNode(node.blockNode)
                bind(start, blockNodeHandle.start, node)
                end = createState()
                bind(blockNodeHandle.end, end, node)

                node.elementSuffix.processElementSuffix()
            }
            is ElementNode.Empty -> {
            }
            else -> TODO("Not yet implemented")
        }

        return Handle(start, end)
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

    private fun bind(previous: State, next: State, treeNode: AntlrNode): EpsilonTransition {
        return EpsilonTransition(previous, next, listOf(treeNode)).also {
            it.bind()
        }
    }

    private fun Transition.bind(): Transition {
        source.outTransitions.add(this)
        target.inTransitions.add(this)
        return this
    }

    private fun createState(): State = State(mutableListOf(), mutableListOf(), stateCounter++)
}