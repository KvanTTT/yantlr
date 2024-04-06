package infrastructure

import AntlrTreeVisitor
import parser.AntlrLexer
import parser.AntlrTreeNode
import parser.AntlrToken

class AntlrPrettier(val lexer: AntlrLexer? = null) : AntlrTreeVisitor<Unit>() {
    private var indentLevel = 0
    private val indentCache = mutableMapOf<Int, String>()
    private val result = StringBuilder()

    fun prettify(node: AntlrTreeNode): String {
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

    override fun visitTreeNode(node: AntlrTreeNode) {
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
            token.value?.let {
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