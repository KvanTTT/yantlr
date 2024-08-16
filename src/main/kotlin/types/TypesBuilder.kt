package types

import atn.Interval
import getCharCode
import parser.AlternativeNode
import parser.AntlrToken
import parser.AntlrTokenType
import parser.AntlrTreeNode
import parser.BlockNode
import parser.ElementBody
import parser.ElementNode
import declarations.DeclarationsInfo
import declarations.Rule

class TypesBuilder(
    private val declarationsInfo: DeclarationsInfo,
    private val diagnosticReporter: ((SemanticsDiagnostic) -> Unit)? = null
) {
    fun build(): TypesInfo {
        val visitor = TypeBuilderVisitor()

        val lexerModeTypes = mutableListOf<ModeType>()
        val lexerRuleTypes = mutableListOf<RuleType>()
        val parserRuleTypes = mutableListOf<RuleType>()

        for (mode in declarationsInfo.lexerModes.values) {
            val modeTypes = mutableListOf<RuleType>()

            for (rule in mode.rules.values) {
                val ruleType = buildRuleType(rule, visitor)
                modeTypes.add(ruleType)

                if (rule.isFragment || rule.isRecursive) {
                    lexerRuleTypes.add(ruleType)
                }
            }

            val resultModeType = if (modeTypes.size > 1) {
                UnionType(modeTypes, mode.modeTreeNode, name = null)
            } else if (modeTypes.size == 1) {
                modeTypes.single()
            } else {
                EmptyType(mode.modeTreeNode, name = null)
            }

            lexerModeTypes.add(ModeType(mode, resultModeType))
        }

        for (parserRule in declarationsInfo.parserRules.values) {
            parserRuleTypes.add(buildRuleType(parserRule, visitor))
        }

        return TypesInfo(lexerModeTypes, lexerRuleTypes, parserRuleTypes)
    }

    private fun buildRuleType(rule: Rule, visitor: TypeBuilderVisitor): RuleType {
        val childType = visitor.visitBlockNode(rule.treeNode.blockNode, null)
        return RuleType(rule, childType)
    }

    inner class TypeBuilderVisitor : AntlrTreeVisitor<Type, Nothing?>() {
        override fun visitTreeNode(node: AntlrTreeNode, data: Nothing?): Type {
            return node.acceptChildren(this, data)!!
        }

        override fun visitToken(token: AntlrToken, data: Nothing?): Type {
            error("Should not be here")
        }

        override fun visitBlockNode(node: BlockNode, data: Nothing?): Type {
            val alternativeNodeType = visitAlternativeNode(node.alternativeNode, data)

            return if (node.orAlternativeNodes.isEmpty()) {
                alternativeNodeType
            } else {
                val types = buildList {
                    add(alternativeNodeType)
                    addAll(node.orAlternativeNodes.map { visitAlternativeNode(it.alternativeNode, data) })
                }
                UnionType(types, node, data)
            }
        }

        override fun visitAlternativeNode(node: AlternativeNode, data: Nothing?): Type {
            return if (node.elementNodes.size == 1) {
                visitElementNode(node.elementNodes.single(), null)
            } else {
                SequenceType(node.elementNodes.map { visitElementNode(it, null) }, node, null)
            }
        }

        override fun visitElementNode(node: ElementNode, data: Nothing?): Type {
            val typeName = node.elementPrefix?.label?.value

            val elementSuffix = node.elementSuffix
            val elementTypeName = if (elementSuffix != null) typeName else null

            val elementBody = node.elementBody
            val resultType: Type = when (elementBody) {
                is ElementBody.StringLiteralOrRange -> {
                    if (elementBody.range == null) {
                        val chars = elementBody.stringLiteral.chars
                        if (chars.isNotEmpty()) {
                            val types = buildList {
                                for (charToken in chars) {
                                    val interval = Interval(charToken.getCharCode(stringLiteral = true))
                                    add(AtomType(interval, charToken, elementTypeName))
                                }
                            }
                            if (chars.size == 1) {
                                types.single()
                            } else {
                                SequenceType(types, elementBody, elementTypeName)
                            }
                        } else {
                            //diagnosticReporter?.invoke(EmptyStringOrSet(elementBody))
                            EmptyType(elementBody, elementTypeName)
                        }
                    } else {
                        fun ElementBody.StringLiteralOrRange.StringLiteral.getBound(): Int? = when {
                            chars.isEmpty() -> {
                                //diagnosticReporter?.invoke(EmptyStringOrSet(this))
                                null
                            }
                            chars.size > 1 -> {
                                //diagnosticReporter?.invoke(MultiCharacterLiteralInRange(this))
                                null
                            }
                            else -> {
                                chars.first().getCharCode(stringLiteral = true)
                            }
                        }

                        val startBound = elementBody.stringLiteral.getBound()
                        val endBound = elementBody.range.stringLiteral.getBound()

                        val interval = if (startBound != null && endBound != null) {
                            if (endBound >= startBound) {
                                Interval(startBound, endBound)
                            } else {
                                //diagnosticReporter?.invoke(ReversedInterval(elementBody))
                                Interval.Empty
                            }
                        } else {
                            Interval.Empty
                        }

                        AtomType(interval, elementBody, elementTypeName)
                    }
                }

                is ElementBody.CharSet -> {
                    if (elementBody.children.isNotEmpty()) {
                        val types = buildList {
                            for (child in elementBody.children) {
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
                                    //diagnosticReporter?.invoke(ReversedInterval(child))
                                    Interval.Empty
                                }
                                add(AtomType(interval, child, elementTypeName))
                            }
                        }
                        if (types.size == 1) {
                            types.single()
                        } else {
                            UnionType(types, elementBody, elementTypeName)
                        }
                    } else {
                        //diagnosticReporter?.invoke(EmptyStringOrSet(elementBody))
                        EmptyType(elementBody, elementTypeName)
                    }
                }

                is ElementBody.Block -> {
                    val childType = visitBlockNode(elementBody.blockNode, data)
                    if (elementTypeName != null) {
                        if (childType.name != null) {
                            // TODO: report error about multiple labels
                        }
                        childType.cloneWithNewTypeName(elementTypeName)
                    } else {
                        childType
                    }
                }

                is ElementBody.LexerId -> {
                    val rule = declarationsInfo.lexerRules[elementBody.lexerId.value!!]!! // TODO: handle unresolved rule
                    RuleRefType(rule, elementBody, elementTypeName)
                }

                is ElementBody.ParserId -> {
                    val rule = declarationsInfo.parserRules[elementBody.parserId.value!!]!! // TODO: handle unresolved rule
                    RuleRefType(rule, elementBody, elementTypeName)
                }

                is ElementBody.Dot -> {
                    AtomType(Interval(Interval.MIN, Interval.MAX), elementBody, elementTypeName)
                }

                is ElementBody.Empty -> {
                    EmptyType(elementBody, elementTypeName)
                }
            }

            return if (elementSuffix != null) {
                val ebnf = elementSuffix.ebnf
                when (ebnf.type) {
                    AntlrTokenType.Question -> {
                        OptionalType(resultType, node, typeName)
                    }
                    AntlrTokenType.Star -> {
                        StarIterationType(resultType, node, typeName)
                    }
                    AntlrTokenType.Plus -> {
                        PlusIterationType(resultType, node, typeName)
                    }
                    else -> {
                        error("Unexpected token type: ${ebnf.type}")
                    }
                }
            } else {
                resultType
            }
        }
    }
}