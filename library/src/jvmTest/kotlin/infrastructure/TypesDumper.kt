package infrastructure

import parser.getLineColumnBorders
import types.AtomType
import types.LeafType
import types.MultipleChildrenType
import types.RuleRefType
import types.SingleChildType
import types.Type
import types.TypesInfo

class TypesDumper(val lineOffsets: List<Int>? = null) {
    private var indentLevel = 0
    private val indentCache = mutableMapOf<Int, String>()

    fun dump(typesInfo: TypesInfo): String {
        val result = StringBuilder()
        indentLevel = 0

        fun <T: Type > List<T>.dumpInternal() {
            forEach { result.appendRecursive(it) }
        }

        typesInfo.lexerModeTypes.dumpInternal()
        typesInfo.lexerRuleTypes.dumpInternal()
        typesInfo.parserRuleTypes.dumpInternal()

        return result.toString()
    }

    fun dump(type: Type): String {
        indentLevel = 0
        return StringBuilder().appendRecursive(type).toString()
    }

    private fun StringBuilder.appendRecursive(type: Type) {
        appendIndent()
        append(type::class.simpleName)
        type.name?.let {
            append(" name=")
            append(it)
        }
        append(" pos=")
        val antlrNodeInterval = type.antlrNode.getInterval()
        if (lineOffsets != null) {
            append(antlrNodeInterval.getLineColumnBorders(lineOffsets))
        } else {
            append(antlrNodeInterval)
        }
        when (type) {
            is AtomType -> {
                append(" interval=")
                append(type.interval)
            }
            is RuleRefType -> {
                append(" rule=")
                append(type.refRule.name)
            }
            else -> {}
        }
        append("\n")
        indentLevel++
        when (type) {
            is LeafType -> {}
            is SingleChildType -> appendRecursive(type.childType)
            is MultipleChildrenType -> type.childrenTypes.forEach { appendRecursive(it) }
        }
        indentLevel--
    }

    private fun StringBuilder.appendIndent() {
        append(indentCache.getOrPut(indentLevel) { "  ".repeat(indentLevel) })
    }
}