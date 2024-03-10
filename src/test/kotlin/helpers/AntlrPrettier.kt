package helpers

import AntlrTreeVisitor
import parser.AntlrLexer
import parser.AntlrNode
import parser.AntlrToken

class AntlrPrettier(val lexer: AntlrLexer? = null) : AntlrTreeVisitor<Unit>() {
    private var indentLevel = 0
    private val indentCache = mutableMapOf<Int, String>()
    private val result = StringBuilder()

    fun prettify(node: AntlrNode): String {
        indentLevel = 0
        result.clear()
        visitTreeNode(node)
        return result.toString()
    }

    fun prettify(token: AntlrToken): String {
        indentLevel = 0
        result.clear()
        visitToken(token)
        return result.toString()
    }

    override fun visitTreeNode(node: AntlrNode) {
        result.appendIndent()
        val nodeType = node::class.simpleName!!
        result.append(if (nodeType.endsWith("Node")) nodeType.substring(0, nodeType.length - 4) else nodeType)
        result.append("\n")
        indentLevel++
        node.acceptChildren(this)
        indentLevel--
    }

    override fun visitToken(token: AntlrToken) {
        with (result) {
            appendIndent()
            append("Token (")
            append(token.type)
            (token.value ?: lexer?.getTokenValue(token))?.let {
                append(", ")
                append(it)
            }
            append(")")
            append("\n")
        }
    }

    private fun StringBuilder.appendIndent() {
        append(indentCache.getOrPut(indentLevel) { "  ".repeat(indentLevel) })
    }
}