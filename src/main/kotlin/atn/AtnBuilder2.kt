package atn

import AntlrTreeVisitor
import EmptyStringOrSet
import MultiCharacterLiteralInRange
import ReversedInterval
import SemanticsDiagnostic
import getCharCode
import parser.*
import declarations.DeclarationsInfo
import declarations.Rule
import types.TypesInfo

class AtnBuilder2(
    val typesInfo: TypesInfo,
    private val diagnosticReporter: ((SemanticsDiagnostic) -> Unit)? = null
) {
    private var stateCounter = 0

    data class Handle(val start: State, val end: State)

    fun build(): Atn {
        val modeStartStates = mutableListOf<ModeState>()
        val lexerStartStates = mutableListOf<RuleState>()
        val parserStartStates = mutableListOf<RuleState>()

        fun Rule.createAndBindEndTransition(start: State, end: State) {
            EndTransitionData(this).bind(start, end)
        }

        for (modeType in typesInfo.lexerModeTypes) {
            val modeStartState = ModeState(modeType, stateCounter++)
            val ruleEndStates = mutableListOf<Pair<Rule, State>>()

            for (rule in modeType.childType mode.rules.values) {
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
        val ruleBodyHandle = visitor.visitBlockNode(rule.treeNode.blockNode, emptyList())

        bindEpsilon(ruleState, ruleBodyHandle.start, ruleNode)

        return Handle(ruleState, ruleBodyHandle.end)
    }

    inner class AtnBuilderVisitor(private val declarationsInfo: DeclarationsInfo) : AntlrTreeVisitor<Handle?, List<ElementBody>>() {
        override fun visitTreeNode(node: AntlrTreeNode, data: List<ElementBody>): Handle? {
            return node.acceptChildren(this, data)
        }

        override fun visitToken(token: AntlrToken, data: List<ElementBody>): Handle? {
            return null
        }

        override fun visitBlockNode(node: BlockNode, data: List<ElementBody>): Handle {
            val start = createState()
            val endNodes = mutableListOf<Pair<State, AntlrNode>>()

            fun processAlternative(alternativeNode: AlternativeNode) {
                val altNodeHandle = visitAlternativeNode(alternativeNode, data)
                bindEpsilon(start, altNodeHandle.start, alternativeNode)
                endNodes.add(altNodeHandle.end to alternativeNode)
            }

            processAlternative(node.alternativeNode)
            node.orAlternativeNodes.forEach { processAlternative(it.alternativeNode) }

            val end = createState()
            endNodes.forEach { (endNode, treeNode) -> bindEpsilon(endNode, end, treeNode) }

            return Handle(start, end)
        }

        override fun visitAlternativeNode(node: AlternativeNode, data: List<ElementBody>): Handle {
            val start = createState()
            var end = start

            node.elementNodes.forEach {
                val newHandle = visitElementNode(it, data)
                bindEpsilon(end, newHandle.start, it)
                end = newHandle.end
            }

            return Handle(start, end)
        }

        override fun visitElementNode(node: ElementNode, data: List<ElementBody>): Handle {
            val bodyHandle = visitElementBody(node.elementBody, data)
            val start = bodyHandle.start
            val end = bodyHandle.end

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

            return bodyHandle
        }

        override fun visitElementBody(node: ElementBody, data: List<ElementBody>): Handle {
            val start = createState()
            var end = start

            val newData = if (node.tilde != null) {
                data + node
            } else {
                data
            }

            when (node) {
                is ElementBody.StringLiteralOrRange -> {
                    if (node.range == null) {
                        val chars = node.stringLiteral.chars
                        if (chars.isNotEmpty()) {
                            for (charToken in chars) {
                                val state = createState()
                                val interval = Interval(charToken.getCharCode(stringLiteral = true))
                                IntervalTransitionData(interval, sortedSetOf(charToken), newData).bind(end, state)
                                end = state
                            }
                        } else {
                            val diagnostic = EmptyStringOrSet(node)
                            diagnosticReporter?.invoke(diagnostic)
                            end = createState()
                            EpsilonTransitionData(node).bind(start, end)
                        }
                    } else {
                        fun ElementBody.StringLiteralOrRange.StringLiteral.getBound(): Int? = when {
                            chars.isEmpty() -> {
                                diagnosticReporter?.invoke(EmptyStringOrSet(this))
                                null
                            }
                            chars.size > 1 -> {
                                diagnosticReporter?.invoke(MultiCharacterLiteralInRange(this))
                                null
                            }
                            else -> {
                                chars.first().getCharCode(stringLiteral = true)
                            }
                        }

                        val startBound = node.stringLiteral.getBound()
                        val endBound = node.range.stringLiteral.getBound()
                        val state = createState()
                        val interval = if (startBound != null && endBound != null) {
                            if (endBound >= startBound) {
                                Interval(startBound, endBound)
                            } else {
                                val diagnostic = ReversedInterval(node)
                                diagnosticReporter?.invoke(diagnostic)
                                Interval.Empty
                            }
                        } else {
                            Interval.Empty
                        }
                        IntervalTransitionData(interval, sortedSetOf(node), newData).bind(end, state)
                        end = state
                    }
                }

                is ElementBody.CharSet -> {
                    end = createState()
                    if (node.children.isNotEmpty()) {
                        for (child in node.children) {
                            val startChar = child.char.getCharCode(stringLiteral = false)
                            val endChar = if (child.range != null) {
                                child.range.char.getCharCode(stringLiteral = false)
                            } else {
                                startChar
                            }
                            // Split set by intervals to make it possible to optimize them later
                            val interval = if (endChar >= startChar) {
                                Interval(startChar, endChar)
                            } else {
                                diagnosticReporter?.invoke(ReversedInterval(child))
                                Interval.Empty
                            }
                            IntervalTransitionData(interval, sortedSetOf(child), newData).bind(start, end)
                        }
                    } else {
                        IntervalTransitionData(Interval.Empty, sortedSetOf(node), newData).bind(start, end)
                        diagnosticReporter?.invoke(EmptyStringOrSet(node))
                    }
                }

                is ElementBody.Block -> {
                    val blockNodeHandle = visitBlockNode(node.blockNode, newData)
                    bindEpsilon(start, blockNodeHandle.start, node)
                    end = createState()
                    bindEpsilon(blockNodeHandle.end, end, node)
                }

                is ElementBody.LexerId -> {
                    end = createState()
                    val rule = declarationsInfo.lexerRules[node.lexerId.value!!]!! // TODO: handle unresolved rule
                    RuleTransitionData(rule, sortedSetOf(node), newData).bind(start, end)
                }

                is ElementBody.ParserId -> {
                    end = createState()
                    val rule = declarationsInfo.parserRules[node.parserId.value!!]!! // TODO: handle unresolved rule
                    RuleTransitionData(rule, sortedSetOf(node), newData).bind(start, end)
                }

                is ElementBody.Dot -> {
                    end = createState()
                    IntervalTransitionData(Interval(Interval.MIN, Interval.MAX), sortedSetOf(node), newData).bind(start, end)
                }

                is ElementBody.Empty -> {
                    end = createState()
                    bindEpsilon(start, end, node)
                }
            }

            return Handle(start, end)
        }
    }

    private fun bindEpsilon(previous: State, next: State, treeNode: AntlrNode): Transition<*> {
        return EpsilonTransitionData(treeNode).bind(previous, next)
    }

    private fun createState(): State = State(stateCounter++)
}