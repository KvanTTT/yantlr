class AntlrTreeNodePrettier {
    companion object {
        private val indentCache = mutableMapOf<Int, String>()

        fun prettify(node: AntlrTreeNode): String {
            val builder = StringBuilder().apply { appendPrettied(node, 0) }
            return builder.toString()
        }

        private fun StringBuilder.appendPrettied(node: AntlrTreeNode, indentLevel: Int) {
            append(indentCache.getOrPut(indentLevel) { "  ".repeat(indentLevel) })
            val nodeType = node.nodeType.toString()
            append(if (nodeType.endsWith("Rule")) nodeType.substring(0, nodeType.length - 4) else nodeType)
            if (node is TokenTreeNode) {
                append(" (")
                append(node.token.type)
                if (node.token.value != null) {
                    append(", ")
                    append(node.token.value)
                }
                append(")")
                append("\n")
            } else {
                append("\n")
                for (child in node.children) {
                    appendPrettied(child, indentLevel + 1)
                }
            }
        }
    }
}