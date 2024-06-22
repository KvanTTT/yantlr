package atn

import AntlrTreeVisitor
import EmptyStringOrSet
import MultiCharacterLiteralInRange
import ReversedInterval
import SemanticsDiagnostic
import parser.*
import semantics.DeclarationsInfo
import semantics.Rule

class AtnBuilder(private val diagnosticReporter: ((SemanticsDiagnostic) -> Unit)? = null) {
    private var stateCounter = 0

    data class Handle(val start: State, val end: State)

    fun build(declarationsInfo: DeclarationsInfo): Atn {
        val visitor = AtnBuilderVisitor(declarationsInfo)
        val modeStartStates = mutableListOf<ModeState>()
        val lexerStartStates = mutableListOf<RuleState>()
        val parserStartStates = mutableListOf<RuleState>()

        fun Rule.createAndBindEndTransition(start: State, end: State) {
            EndTransitionData(this, listOf(treeNode)).bind(start, end)
        }

        for (mode in declarationsInfo.lexerModes.values) {
            val modeStartState = ModeState(mode, stateCounter++)
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

                bindEpsilon(modeStartState, ruleState, rule.treeNode)
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
        val ruleNode = rule.treeNode
        val ruleState = RuleState(rule, stateCounter++)
        val ruleBodyHandle = visitor.visitBlockNode(rule.treeNode.blockNode)

        bindEpsilon(ruleState, ruleBodyHandle.start, ruleNode)

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
                bindEpsilon(start, altNodeHandle.start, alternativeNode)
                endNodes.add(altNodeHandle.end to alternativeNode)
            }

            processAlternative(node.alternativeNode)
            node.orAlternativeNodes.forEach { processAlternative(it.alternativeNode) }

            val end = createState()
            endNodes.forEach { (endNode, treeNode) -> bindEpsilon(endNode, end, treeNode) }

            return Handle(start, end)
        }

        override fun visitAlternativeNode(node: AlternativeNode): Handle {
            val start = createState()
            var end = start

            node.elementNodes.forEach {
                val newHandle = visitElementNode(it)
                bindEpsilon(end, newHandle.start, it)
                end = newHandle.end
            }

            return Handle(start, end)
        }

        override fun visitElementNode(node: ElementNode): Handle {
            val start = createState()
            var end = start

            when (node) {
                is ElementNode.StringLiteralOrRange -> {
                    if (node.range == null) {
                        val chars = node.stringLiteral.chars
                        if (chars.isNotEmpty()) {
                            for (charToken in chars) {
                                val state = createState()
                                val interval = Interval(getCharCode(charToken, stringLiteral = true))
                                IntervalTransitionData(interval, listOf(charToken)).bind(end, state)
                                end = state
                            }
                        } else {
                            val diagnostic = EmptyStringOrSet(node)
                            diagnosticReporter?.invoke(diagnostic)
                            end = createState()
                            ErrorTransitionData(diagnostic, listOf(node)).bind(start, end)
                        }
                    } else {
                        fun ElementNode.StringLiteralOrRange.StringLiteral.getBound(): Pair<Int?, SemanticsDiagnostic?> = when {
                            chars.isEmpty() -> {
                                val diagnostic = EmptyStringOrSet(this)
                                diagnosticReporter?.invoke(EmptyStringOrSet(this))
                                null to diagnostic
                            }
                            chars.size > 1 -> {
                                val diagnostic = MultiCharacterLiteralInRange(this)
                                diagnosticReporter?.invoke(diagnostic)
                                null to diagnostic
                            }
                            else -> {
                                getCharCode(chars.first(), stringLiteral = true) to null
                            }
                        }

                        val (startBound, startDiagnostic) = node.stringLiteral.getBound()
                        val (endBound, endDiagnostic) = node.range.stringLiteral.getBound()
                        val state = createState()
                        if (startBound != null && endBound != null) {
                            if (endBound >= startBound) {
                                IntervalTransitionData(Interval(startBound, endBound), listOf(node))
                            } else {
                                val diagnostic = ReversedInterval(node)
                                diagnosticReporter?.invoke(diagnostic)
                                ErrorTransitionData(diagnostic, listOf(node))
                            }.bind(end, state)
                        } else {
                            startDiagnostic?.let {
                                ErrorTransitionData(it, listOf(node)).bind(end, state)
                            }
                            endDiagnostic?.let {
                                ErrorTransitionData(it, listOf(node)).bind(end, state)
                            }
                        }
                        end = state
                    }
                }

                is ElementNode.CharSet -> {
                    if (node.children.isNotEmpty()) {
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
                                if (endChar >= startChar) {
                                    IntervalTransitionData(Interval(startChar, endChar), listOf(child))
                                } else {
                                    val diagnostic = ReversedInterval(child)
                                    diagnosticReporter?.invoke(diagnostic)
                                    ErrorTransitionData(diagnostic, listOf(child))
                                }.bind(start, it)
                                endStates.add(it)
                            }
                        }
                        end = createState()
                        endStates.forEach { bindEpsilon(it, end, node) }
                    } else {
                        end = createState()
                        EmptyStringOrSet(node).also {
                            ErrorTransitionData(it, listOf(node)).bind(start, end)
                            diagnosticReporter?.invoke(it)
                        }
                    }
                }

                is ElementNode.Block -> {
                    val blockNodeHandle = visitBlockNode(node.blockNode)
                    bindEpsilon(start, blockNodeHandle.start, node)
                    end = createState()
                    bindEpsilon(blockNodeHandle.end, end, node)
                }

                is ElementNode.LexerId -> {
                    end = createState()
                    val rule = declarationsInfo.lexerRules[node.lexerId.value!!]!! // TODO: handle unresolved rule
                    RuleTransitionData(rule, listOf(node)).bind(start, end)
                }

                is ElementNode.ParserId -> {
                    end = createState()
                    val rule = declarationsInfo.parserRules[node.parserId.value!!]!! // TODO: handle unresolved rule
                    RuleTransitionData(rule, listOf(node)).bind(start, end)
                }

                is ElementNode.Empty -> {
                    end = createState()
                    bindEpsilon(start, end, node)
                }
            }

            // TODO: implement greedy processing
            node.elementSuffix?.let {
                val ebnf = it.ebnf
                when (ebnf.type) {
                    AntlrTokenType.Question -> {
                        bindEpsilon(start, end, ebnf)
                    }
                    AntlrTokenType.Star -> {
                        bindEpsilon(start, end, ebnf)
                        bindEpsilon(end, start, ebnf)
                    }
                    AntlrTokenType.Plus -> {
                        bindEpsilon(end, start, ebnf)
                    }
                    else -> {
                        error("Unexpected token type: ${ebnf.type}")
                    }
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

    private fun bindEpsilon(previous: State, next: State, treeNode: AntlrNode): Transition<*> {
        return EpsilonTransitionData(listOf(treeNode)).bind(previous, next)
    }

    private fun createState(): State = State(stateCounter++)
}