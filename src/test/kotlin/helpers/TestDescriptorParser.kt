package helpers

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.visitors.RecursiveVisitor
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

class TestDescriptorExtractor : RecursiveVisitor() {
    companion object {
        val testDescriptorProperties = TestDescriptor::class.declaredMemberProperties.associateBy { it.name }
    }

    private var input: String = ""
    private val propertyValues: MutableMap<KProperty1<TestDescriptor, *>, Any?> = mutableMapOf()
    private var currentProperty: KProperty1<TestDescriptor, *>? = null
    private var currentPropertyValue: Any? = null

    @Suppress("UNCHECKED_CAST")
    fun extract(name: String, input: String): TestDescriptor {
        this.input = input
        val parser = MarkdownParser(CommonMarkFlavourDescriptor())
        val tree = parser.buildMarkdownTreeFromString(input)
        visitNode(tree)
        finalizePreviousProperty()
        // TODO: report missing properties
        return TestDescriptor(
            name = name,
            notes = getPropertyValue("notes") as String?,
            grammars = getPropertyValue("grammars") as List<String>,
            input = getPropertyValue("input") as String?,
        )
    }

    private fun getPropertyValue(name: String): Any? {
        return testDescriptorProperties[name]?.let { propertyValues[it] }
    }

    override fun visitNode(node: ASTNode) {
        when (node.type) {
            MarkdownElementTypes.ATX_1 -> processHeader(node)
            MarkdownElementTypes.PARAGRAPH -> processParagraph(node)
            MarkdownElementTypes.CODE_FENCE -> processCodeFence(node)
            else -> super.visitNode(node)
        }
    }

    private fun processHeader(headerNode: ASTNode) {
        finalizePreviousProperty()

        val firstChild = headerNode.children.elementAt(1)
        val lastChild = headerNode.children.last()
        val name = input.subSequence(firstChild.startOffset, lastChild.endOffset)
            .trim().toString().lowercase()
        // TODO: report not found property
        val descriptorProperty = testDescriptorProperties[name]!!
        if (!propertyValues.containsKey(descriptorProperty)) {
            currentProperty = descriptorProperty
            currentPropertyValue = null
        } else {
            // TODO: report duplicate property
        }
    }

    private fun finalizePreviousProperty() {
        val property = currentProperty
        if (property != null) {
            propertyValues[property] = currentPropertyValue
            currentProperty = null
            currentPropertyValue = null
        }
    }

    private fun processParagraph(paragraphNode: ASTNode) {
        processText(paragraphNode.startOffset, paragraphNode.endOffset,  shouldTrim = true)
    }

    private fun processCodeFence(codeFenceNode: ASTNode) {
        val firstEol = codeFenceNode.children.first { it.type == MarkdownTokenTypes.EOL }
        val lastEol = codeFenceNode.children.last { it.type == MarkdownTokenTypes.EOL }
        val endOffset = if (lastEol.startOffset < firstEol.endOffset) firstEol.endOffset else lastEol.startOffset
        processText(firstEol.endOffset, endOffset, shouldTrim = false)
    }

    @Suppress("UNCHECKED_CAST")
    private fun processText(startOffset: Int, endOffset: Int, shouldTrim: Boolean) {
        val property = currentProperty
        if (property == null) {
            // TODO: report missing descriptor property
            return
        }

        val subsequent = input.subSequence(startOffset, endOffset).let { if (shouldTrim) it.trim() else it }.toString()
        if (property.returnType.classifier == List::class) {
            val value = (currentPropertyValue ?: run {
                currentPropertyValue = mutableListOf<String>()
                currentPropertyValue
            }) as MutableList<String>
            value.add(subsequent)
        } else {
            val value = currentPropertyValue as? String
            if (value != null) {
                // TODO: report duplicated value
            } else {
                currentPropertyValue = subsequent
            }
        }
    }
}