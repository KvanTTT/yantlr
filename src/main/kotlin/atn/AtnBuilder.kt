package atn

import AntlrTreeVisitor
import SemanticsDiagnostics
import parser.*
import semantics.DeclarationsInfo
import semantics.Rule

class AtnBuilder(private val diagnosticReporter: ((SemanticsDiagnostics) -> Unit)? = null) {
    private var stateCounter = 0

    data class Handle(val start: State, val end: State)

    fun build(declarationsInfo: DeclarationsInfo): Atn {
        val visitor = AtnBuilderVisitor(declarationsInfo)
        val modeStartStates = mutableListOf<ModeState>()
        val lexerStartStates = mutableListOf<RuleState>()
        val parserStartStates = mutableListOf<RuleState>()

        for (mode in declarationsInfo.lexerModes.values) {
            val modeStartState = ModeState(mode, mutableListOf(), stateCounter++)

            for (rule in mode.rules.values) {
                val ruleState = buildRule(rule, visitor)

                if (rule.isFragment || rule.isRecursive) {
                    lexerStartStates.add(AtnCloner.clone(ruleState))
                }

                bindWithNextComputed(modeStartState, ruleState, rule.ruleNode)
            }

            modeStartStates.add(modeStartState)
        }

        for (parserRule in declarationsInfo.parserRules.values) {
            parserStartStates.add(buildRule(parserRule, visitor))
        }

        return Atn(modeStartStates, lexerStartStates, parserStartStates)
    }

    private fun buildRule(rule: Rule, visitor: AtnBuilderVisitor): RuleState {
        val ruleNode = rule.ruleNode
        val ruleState = RuleState(rule, mutableListOf(), stateCounter++)
        val ruleBodyHandle = visitor.visitBlockNode(rule.ruleNode.blockNode)

        bindWithNextComputed(ruleState, ruleBodyHandle.start, ruleNode)
        EndTransition(rule, ruleBodyHandle.end, createState(), listOf(ruleNode)).bindWithPreviousComputed()

        return ruleState
    }

    inner class AtnBuilderVisitor(private val declarationsInfo: DeclarationsInfo) : AntlrTreeVisitor<Handle?>() {
        override fun visitTreeNode(node: AntlrTreeNode): Handle? {
            return node.acceptChildren(this)
        }

        override fun visitToken(token: AntlrToken): Handle? {
            return null
        }

        override fun visitBlockNode(node: BlockNode): Handle {
            val start = createState()
            val end by lazy(LazyThreadSafetyMode.NONE) { createState() }

            fun processAlternative(alternativeNode: AlternativeNode) {
                val altNodeHandle = visitAlternativeNode(alternativeNode)
                bindWithNextComputed(start, altNodeHandle.start, alternativeNode)
                bindWithPreviousComputed(altNodeHandle.end, end, alternativeNode)
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
                bindWithNextComputed(end, newHandle.start, it)
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
                        bindWithPreviousComputed(start, end, ebnf)
                    }

                    AntlrTokenType.Star -> {
                        bindWithPreviousComputed(start, end, ebnf)
                        bindWithPreviousComputed(end, start, ebnf)
                    }

                    AntlrTokenType.Plus -> {
                        bindWithPreviousComputed(end, start, ebnf)
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
                        SetTransition(intervalSet, end, state, listOf(charToken)).bindWithPreviousComputed()
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
                    SetTransition(IntervalSet(intervals), end, state, treeNodes).bindWithPreviousComputed()
                    end = state

                    node.elementSuffix.processElementSuffix()
                }

                is ElementNode.Block -> {
                    val blockNodeHandle = visitBlockNode(node.blockNode)
                    bindWithNextComputed(start, blockNodeHandle.start, node)
                    end = createState()
                    bindWithPreviousComputed(blockNodeHandle.end, end, node)

                    node.elementSuffix.processElementSuffix()
                }

                is ElementNode.LexerId -> {
                    end = createState()
                    val rule = declarationsInfo.lexerRules[node.lexerId.value!!]!! // TODO: handle unresolved rule
                    RuleTransition(rule, start, end, listOf(node)).bindWithPreviousComputed()

                    node.elementSuffix.processElementSuffix()
                }

                is ElementNode.ParserId -> {
                    end = createState()
                    val rule = declarationsInfo.parserRules[node.parserId.value!!]!! // TODO: handle unresolved rule
                    RuleTransition(rule, start, end, listOf(node)).bindWithPreviousComputed()

                    node.elementSuffix.processElementSuffix()
                }

                is ElementNode.Empty -> {
                }

                else -> TODO("Not yet implemented for $node")
            }

            return Handle(start, end)
        }

        private fun getCharCode(charToken: AntlrToken, stringLiteral: Boolean): Int {
            val literalToEscapeChars =
                if (stringLiteral) antlrStringLiteralToEscapeChars else antlrCharSetLiteralToEscapeChars
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

    private fun bindWithNextComputed(previous: State, next: State, treeNode: AntlrNode): EpsilonTransition =
        bind(previous, next, treeNode, inTransitionToBegin = true)

    private fun bindWithPreviousComputed(previous: State, next: State, treeNode: AntlrNode): EpsilonTransition =
        bind(previous, next, treeNode, inTransitionToBegin = false)

    private fun bind(previous: State, next: State, treeNode: AntlrNode, inTransitionToBegin: Boolean): EpsilonTransition {
        return EpsilonTransition(previous, next, listOf(treeNode)).also {
            it.bind(inTransitionToBegin)
        }
    }

    private fun Transition.bindWithNextComputed(): Transition = bind(inTransitionToBegin = true)

    private fun Transition.bindWithPreviousComputed(): Transition = bind(inTransitionToBegin = false)

    private fun Transition.bind(inTransitionToBegin: Boolean): Transition {
        target.inTransitions.let {
            if (inTransitionToBegin) {
                it.add(0, this)
            } else {
                it.add(this)
            }
        }
        source.outTransitions.add(this)
        return this
    }

    private fun createState(): State = State(mutableListOf(), mutableListOf(), stateCounter++)
}