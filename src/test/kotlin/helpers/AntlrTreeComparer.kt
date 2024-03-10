package helpers

import parser.AntlrLexer
import parser.AntlrNode
import parser.AntlrToken
import kotlin.math.min
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals
import kotlin.test.assertFails

class AntlrTreeComparer(val lexer: AntlrLexer?) {
    fun compare(expectedNode: Any?, actualNode: Any?, actualNodePropertyName: String? = null): Boolean {
        if (expectedNode == null && actualNode == null) {
            return true
        }

        if (expectedNode == null || actualNode == null) {
            assertFails("Expected node: ${expectedNode.getNodeInfo()}, actual node: ${actualNode.getNodeInfo()}") {}
            return false
        }

        if (expectedNode is AntlrToken) {
            return compareToken(expectedNode, actualNode)
        }

        if (expectedNode is List<*>) {
            return compareCollection(expectedNode, actualNode, actualNodePropertyName)
        }

        val expectedNodeClass = expectedNode::class

        assertEquals(
            expectedNodeClass, actualNode::class,
            "Expected node: ${expectedNode.getNodeInfo()}, actual node: ${actualNode.getNodeInfo()}"
        )

        for (expectedMemberProperty in expectedNodeClass.memberProperties) {
            @Suppress("UNCHECKED_CAST")
            val property = expectedMemberProperty as KProperty1<Any, *>
            if (!compare(property.get(expectedNode), property.get(actualNode), property.name)) {
                return false
            }
        }

        return true
    }

    fun compareToken(expectedToken: AntlrToken, actualNode: Any?): Boolean {
        if (actualNode !is AntlrToken) {
            assertFails("Expected $expectedToken, actual node: ${actualNode.getNodeInfo()}") {}
            return false
        }

        if (expectedToken.type != actualNode.type ||
            expectedToken.channel != actualNode.channel ||
            expectedToken.value != null && expectedToken.value != lexer?.getTokenValue(actualNode)
        ) {
            assertEquals(
                expectedToken.renderTokenInfo(),
                actualNode.renderTokenInfo(),
                actualNode.getPositionSuffix()
            )
        }

        return true
    }

    fun compareCollection(expectedNodes: List<*>, actualNodes: Any?, actualNodePropertyName: String?): Boolean {
        if (actualNodes !is List<*>) {
            assertFails("Expected list, actual node: $actualNodes${getPositionSuffix()}") {}
            return false
        }

        val minSize = min(expectedNodes.size, actualNodes.size)

        for (i in 0..<minSize) {
            if (!compare(expectedNodes[i], actualNodes[i])) {
                return false
            }
        }

        if (expectedNodes.size != actualNodes.size) {
            val info = if (expectedNodes.size < actualNodes.size) {
                "Extra node: ${actualNodes[expectedNodes.size].getNodeInfo()}"
            } else {
                "Missing node after: ${expectedNodes[actualNodes.size].getNodeInfo()}"
            }
            assertFails("Expected $actualNodePropertyName size: ${expectedNodes.size}, but actual is: ${actualNodes.size}. ${info}") {}
        }

        return true
    }

    private fun AntlrToken.renderTokenInfo(): String =
        buildString {
            append("type=")
            append(type)
            appendLine()
            append("channel=")
            append(channel)
            appendLine()
            if (this@renderTokenInfo.value != null || this@renderTokenInfo.offset != -1 && this@renderTokenInfo.length > 0) {
                append("value=")
                append(lexer?.getTokenValue(this@renderTokenInfo))
            }
        }

    private fun Any?.getNodeInfo(): String {
        if (this == null) return "null"

        val tokenValue = (this as? AntlrToken)?.let { lexer?.getTokenValue(it) }?.let { " ($it)" } ?: ""
        return this::class.simpleName + tokenValue + getPositionSuffix()
    }

    private fun Any?.getPositionSuffix(): String {
        val actualLeftToken = (this as? AntlrToken) ?: (this as? AntlrNode)?.leftToken
        val positionSuffix = if (actualLeftToken != null && actualLeftToken.offset != -1) {
            " at " + (lexer?.getLineColumn(actualLeftToken.offset) ?: actualLeftToken.offset.toString())
        } else {
            ""
        }
        return positionSuffix
    }
}