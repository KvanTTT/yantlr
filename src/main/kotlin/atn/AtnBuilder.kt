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

        fun Rule.createAndBindEndTransition(start: State, end: State) {
            EndTransition(this, start, end, listOf(ruleNode)).bind()
        }

        for (mode in declarationsInfo.lexerModes.values) {
            val modeStartState = ModeState(mode, mutableListOf(), stateCounter++)
            val ruleEndStates = mutableListOf<Pair<Rule, State>>()

            for (rule in mode.rules.values) {
                val (ruleState, end) = buildRule(rule, visitor)
                ruleEndStates.add(rule to end)

                if (rule.isFragment || rule.isRecursive) {
                    val cloneInfo = AtnCloner.clone(ruleState as RuleState, stateCounter)
                    stateCounter = cloneInfo.stateCounter
                    rule.createAndBindEndTransition(cloneInfo.getMappedState(end), createState())
                    lexerStartStates.add(cloneInfo.getMappedState(ruleState) as RuleState)
                }

                bind(modeStartState, ruleState, rule.ruleNode)
            }
            val modeEnd = createState()
            ruleEndStates.forEach { (rule, ruleEnd) -> rule.createAndBindEndTransition(ruleEnd, modeEnd) }

            modeStartStates.add(modeStartState)
        }

        for (parserRule in declarationsInfo.parserRules.values) {
            val (ruleState, end) = buildRule(parserRule, visitor)
            parserRule.createAndBindEndTransition(end, createState())
            parserStartStates.add(ruleState as RuleState)
        }

        return Atn(modeStartStates, lexerStartStates, parserStartStates, stateCounter)
    }

    private fun buildRule(rule: Rule, visitor: AtnBuilderVisitor): Handle {
        val ruleNode = rule.ruleNode
        val ruleState = RuleState(rule, mutableListOf(), stateCounter++)
        val ruleBodyHandle = visitor.visitBlockNode(rule.ruleNode.blockNode)

        bind(ruleState, ruleBodyHandle.start, ruleNode)

        return Handle(ruleState, ruleBodyHandle.end)
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
            val endNodes = mutableListOf<Pair<State, AntlrNode>>()

            fun processAlternative(alternativeNode: AlternativeNode) {
                val altNodeHandle = visitAlternativeNode(alternativeNode)
                bind(start, altNodeHandle.start, alternativeNode)
                endNodes.add(altNodeHandle.end to alternativeNode)
            }

            processAlternative(node.alternativeNode)
            node.orAlternativeNodes.forEach { processAlternative(it.alternativeNode) }

            val end = createState()
            endNodes.forEach { (endNode, treeNode) -> bind(endNode, end, treeNode) }

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
                    val endStates = mutableListOf<State>()
                    for (child in node.children) {
                        val startChar = getCharCode(child.char, stringLiteral = false)
                        val endChar = if (child.range != null) {
                            getCharCode(child.range.char, stringLiteral = false)
                        } else {
                            startChar
                        }
                        // Split set by intervals to make it possible to optimize them later
                        createState().also {
                            SetTransition(IntervalSet(startChar, endChar), start, it, listOf(child)).bind()
                            endStates.add(it)
                        }
                    }

                    end = createState()
                    endStates.forEach { bind(it, end, node) }

                    node.elementSuffix.processElementSuffix()
                }

                is ElementNode.Block -> {
                    val blockNodeHandle = visitBlockNode(node.blockNode)
                    bind(start, blockNodeHandle.start, node)
                    end = createState()
                    bind(blockNodeHandle.end, end, node)

                    node.elementSuffix.processElementSuffix()
                }

                is ElementNode.LexerId -> {
                    end = createState()
                    val rule = declarationsInfo.lexerRules[node.lexerId.value!!]!! // TODO: handle unresolved rule
                    RuleTransition(rule, start, end, listOf(node)).bind()

                    node.elementSuffix.processElementSuffix()
                }

                is ElementNode.ParserId -> {
                    end = createState()
                    val rule = declarationsInfo.parserRules[node.parserId.value!!]!! // TODO: handle unresolved rule
                    RuleTransition(rule, start, end, listOf(node)).bind()

                    node.elementSuffix.processElementSuffix()
                }

                is ElementNode.Empty -> {
                    end = createState()
                    bind(start, end, node)
                }
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

    private fun bind(previous: State, next: State, treeNode: AntlrNode): EpsilonTransition {
        return EpsilonTransition(previous, next, listOf(treeNode)).also {
            it.bind()
        }
    }

    private fun Transition.bind(): Transition {
        target.inTransitions.add(this)
        source.outTransitions.add(this)
        return this
    }

    private fun createState(): State = State(mutableListOf(), mutableListOf(), stateCounter++)
}