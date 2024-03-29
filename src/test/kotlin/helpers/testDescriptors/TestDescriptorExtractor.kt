package helpers.testDescriptors

import java.io.File
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

class TestDescriptorExtractor private constructor(file: File) {
    companion object {
        private val testDescriptorProperties = TestDescriptor::class.declaredMemberProperties.associateBy { it.name }
        private val lineBreakChars = charArrayOf('\r', '\n')
        private val whitespaceChars = lineBreakChars + charArrayOf(' ', '\t')

        fun extract(file: File): TestDescriptor {
            return TestDescriptorExtractor(file).extract(file)
        }
    }

    enum class TextType {
        Paragraph,
        Code,
    }

    private var input: String = file.readText()
    private val propertyValues: MutableMap<KProperty1<TestDescriptor, *>, Any?> = mutableMapOf()
    private var property: KProperty1<TestDescriptor, *>? = null
    private var propertyValue: Any? = null
    private var textType: TextType? = null

    fun extract(file: File): TestDescriptor {
        val lineOffsets = mutableListOf<Int>()

        var lineStartOffset = 0
        var previousLineEndOffset = 0
        var elementStartOffset = 0

        do {
            lineOffsets.add(lineStartOffset)

            val lineEndOffset = input.indexOfAny(lineBreakChars, lineStartOffset).let { if (it == -1) input.length else it }
            val nextLineStartOffset = when {
                lineEndOffset == input.length -> lineEndOffset
                input[lineEndOffset] == '\r' && input.getOrNull(lineEndOffset + 1) == '\n' -> {
                    lineEndOffset + 2
                }
                else -> {
                    lineEndOffset + 1
                }
            }

            val lineValue = input.subSequence(lineStartOffset, lineEndOffset)
            if (textType == TextType.Code) {
                if (checkCodeFence(lineValue)) {
                    finalizeTextValue(elementStartOffset, previousLineEndOffset)
                }
            } else if (lineValue.isBlank()) {
                if (textType == TextType.Paragraph) {
                    finalizeTextValue(elementStartOffset, skipBackWhitespaces(lineStartOffset - 1))
                }
            } else {
                val firstChar = lineValue[0]
                when {
                    firstChar == '#' -> {
                        finalizeTextValue(elementStartOffset, skipBackWhitespaces(lineStartOffset - 1))
                        processHeader(lineValue)
                    }
                    firstChar == '`' && checkCodeFence(lineValue) -> {
                        finalizeTextValue(elementStartOffset, skipBackWhitespaces(lineStartOffset - 1))
                        elementStartOffset = nextLineStartOffset
                        textType = TextType.Code
                    }
                    else -> {
                        if (textType == null) {
                            textType = TextType.Paragraph
                            elementStartOffset = lineStartOffset
                        }
                    }
                }
            }

            previousLineEndOffset = lineEndOffset
            lineStartOffset = nextLineStartOffset
        } while (nextLineStartOffset < input.length)

        textType = TextType.Paragraph
        finalizeTextValue(input.length, input.length)
        finalizePreviousProperty()

        // TODO: report missing properties
        @Suppress("UNCHECKED_CAST")
        return TestDescriptor(
            name = file.nameWithoutExtension,
            notes = getPropertyValue("notes") as PropertyValue?,
            grammars = getPropertyValue("grammars") as List<PropertyValue>,
            input = getPropertyValue("input") as PropertyValue?,
        )
    }

    private fun skipBackWhitespaces(offset: Int): Int {
        if (offset <= 0) return 0

        var currentOffset = offset
        while (currentOffset > 0 && input[currentOffset] in whitespaceChars) {
            currentOffset--
        }
        return currentOffset + 1
    }

    private fun processHeader(lineValue: CharSequence) {
        finalizePreviousProperty()

        val headerValue = lineValue.dropWhile { it == '#' }.trim().toString().lowercase()
        // TODO: report not found property
        val descriptorProperty = testDescriptorProperties[headerValue]!!
        if (!propertyValues.containsKey(descriptorProperty)) {
            property = descriptorProperty
            propertyValue = null
        } else {
            // TODO: report duplicate property
        }
    }

    private fun checkCodeFence(lineValue: CharSequence): Boolean =
        lineValue.length >= 3 && lineValue[0] == '`' && lineValue[1] == '`' && lineValue[2] == '`'

    private fun getPropertyValue(name: String): Any? {
        return testDescriptorProperties[name]?.let { propertyValues[it] }
    }

    @Suppress("UNCHECKED_CAST")
    private fun finalizeTextValue(startOffset: Int, endOffset: Int) {
        if (textType == null) return
        textType = null

        val currentProperty = property
        if (currentProperty == null) {
            if (propertyValue != null) {
                // TODO: report missing descriptor property
            }
            return
        }

        val currentPropertyValue = TextPropertyValue(input.subSequence(startOffset, endOffset), startOffset)
        if (currentProperty.returnType.classifier == List::class) {
            val value = (propertyValue ?: run {
                propertyValue = mutableListOf<PropertyValue>()
                propertyValue
            }) as MutableList<PropertyValue>
            value.add(currentPropertyValue)
        } else {
            val value = propertyValue as? PropertyValue
            if (value != null) {
                // TODO: report duplicated value
            } else {
                propertyValue = currentPropertyValue
            }
        }
    }

    private fun finalizePreviousProperty() {
        val currentProperty = property
        if (currentProperty != null) {
            propertyValues[currentProperty] = propertyValue
            property = null
            propertyValue = null
        }
    }
}