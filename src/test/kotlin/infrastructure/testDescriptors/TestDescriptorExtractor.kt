package infrastructure.testDescriptors

import SourceInterval
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

class TestDescriptorExtractor private constructor(
    private val input: String,
    private val name: String,
    private val diagnosticReporter: ((TestDescriptorDiagnostic) -> Unit)? = null
) {
    companion object {
        private val testDescriptorProperties = TestDescriptor::class.declaredMemberProperties.associateBy { it.name }
        private val lineBreakChars = charArrayOf('\r', '\n')
        private const val NOTES_NAME = "notes"

        fun extract(input: String, name: String, diagnosticReporter: ((TestDescriptorDiagnostic) -> Unit)? = null): TestDescriptor {
            return TestDescriptorExtractor(input, name, diagnosticReporter).extract()
        }
    }

    enum class TextType {
        Empty, // Used to mark sections with empty headers
        Paragraph,
        Code,
    }

    data class TextTypeWithOffset(val type: TextType, val offset: Int)

    private val propertyValues: MutableMap<KProperty1<TestDescriptor, *>, Any?> = mutableMapOf()
    // Use `Notes` property for first headless paragraph
    private var property: KProperty1<TestDescriptor, *> = testDescriptorProperties.getValue(NOTES_NAME)
    private var textInfo: TextTypeWithOffset? = null

    fun extract(): TestDescriptor {
        val lineOffsets = mutableListOf<Int>()

        var lineStartOffset = 0
        var previousLineEndOffset = 0

        do {
            lineOffsets.add(lineStartOffset)

            val lineEndOffset =
                input.indexOfAny(lineBreakChars, lineStartOffset).let { if (it == -1) input.length else it }
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
            val firstChar = lineValue.getOrNull(0)

            when {
                textInfo?.type == TextType.Code -> {
                    if (checkCodeFence(lineValue)) {
                        finalizeTextValue(previousLineEndOffset)
                    }
                }

                firstChar == '#' -> {
                    finalizeTextValue(
                        skipBackWhitespaces(lineStartOffset - 1),
                        addEmptyValueIfNoElements = true
                    )
                    processHeader(lineStartOffset, lineEndOffset)
                    textInfo = TextTypeWithOffset(TextType.Empty, lineEndOffset)
                }

                firstChar == '`' && checkCodeFence(lineValue) -> {
                    finalizeTextValue(skipBackWhitespaces(lineStartOffset - 1))
                    textInfo = TextTypeWithOffset(TextType.Code, nextLineStartOffset)
                }

                lineValue.isBlank() -> {
                    if (textInfo?.type == TextType.Paragraph) {
                        finalizeTextValue(skipBackWhitespaces(lineStartOffset - 1))
                    }
                }

                textInfo.let { it == null || it.type == TextType.Empty } -> {
                    textInfo = TextTypeWithOffset(TextType.Paragraph, lineStartOffset)
                }
            }

            previousLineEndOffset = lineEndOffset
            lineStartOffset = nextLineStartOffset
        } while (nextLineStartOffset < input.length)

        // Finalize trailing text blocks
        finalizeTextValue(
            if (textInfo?.type == TextType.Code) previousLineEndOffset else skipBackWhitespaces(lineStartOffset - 1),
            addEmptyValueIfNoElements = true
        )

        @Suppress("UNCHECKED_CAST")
        return TestDescriptor(
            name = (getPropertyValue("name") as? PropertyValue)?.value?.toString() ?: name,
            notes = getPropertyValue(NOTES_NAME) as? List<PropertyValue> ?: emptyList(),
            grammars = getPropertyValue("grammars") as? List<PropertyValue> ?: run {
                diagnosticReporter?.invoke(
                    MissingPropertyDiagnostic("Grammars", SourceInterval(input.length, 0))
                )
                emptyList()
            },
            input = getPropertyValue("input") as? List<PropertyValue> ?: emptyList(),
        )
    }

    private fun skipBackWhitespaces(offset: Int): Int {
        if (offset <= 0) return 0

        var currentOffset = offset
        while (currentOffset > 0 && input[currentOffset].isWhitespace()) {
            currentOffset--
        }
        return currentOffset + 1
    }

    private fun processHeader(lineStart: Int, lineEnd: Int) {
        val subSequence = input.subSequence(lineStart, lineEnd)
        val headerValueStart = subSequence.indexOfFirst { it != '#' && !it.isWhitespace() }
        val headerValueStop = subSequence.indexOfLast { !it.isWhitespace() } + 1
        val headerValue = subSequence.subSequence(headerValueStart, headerValueStop).toString()

        val descriptorProperty = testDescriptorProperties[headerValue.lowercase()]

        if (descriptorProperty != null) {
            val existingPropertyValue = propertyValues[descriptorProperty]
            property = descriptorProperty

            if (existingPropertyValue != null && (existingPropertyValue as? List<*>)?.isNotEmpty() == true) {
                diagnosticReporter?.invoke(
                    DuplicatedPropertyDiagnostic(
                        headerValue,
                        SourceInterval(lineStart + headerValueStart, headerValue.length)
                    )
                )
            }
        } else {
            diagnosticReporter?.invoke(
                UnknownPropertyDiagnostic(
                    headerValue,
                    SourceInterval(lineStart + headerValueStart, headerValue.length)
                )
            )
        }
    }

    private fun checkCodeFence(lineValue: CharSequence): Boolean =
        lineValue.length >= 3 && lineValue[0] == '`' && lineValue[1] == '`' && lineValue[2] == '`'

    private fun getPropertyValue(name: String): Any? {
        return testDescriptorProperties[name]?.let { propertyValues[it] }
    }

    @Suppress("UNCHECKED_CAST")
    private fun finalizeTextValue(endOffset: Int, addEmptyValueIfNoElements: Boolean = false) {
        val localTextInfo = textInfo ?: return

        fun getTextValue(): PropertyValue {
            val sourceInterval = SourceInterval(localTextInfo.offset, endOffset - localTextInfo.offset)
            return TextPropertyValue(input.subSequence(localTextInfo.offset, endOffset), sourceInterval)
        }

        when (property.returnType.classifier) {
            List::class -> {
                val propertyValue = (propertyValues[property] ?: run {
                    mutableListOf<PropertyValue>().also { propertyValues[property] = it }
                }) as MutableList<PropertyValue>
                if (localTextInfo.type != TextType.Empty || propertyValue.isEmpty() && addEmptyValueIfNoElements) {
                    propertyValue.add(getTextValue())
                }
            }
            String::class -> {
                val propertyValue = propertyValues[property] as? PropertyValue
                if (localTextInfo.type != TextType.Empty || propertyValue == null && addEmptyValueIfNoElements) {
                    if (propertyValue == null) {
                        propertyValues[property] = getTextValue()
                    } else {
                        diagnosticReporter?.invoke(
                            DuplicatedValueDiagnostic(
                                property.name,
                                SourceInterval(localTextInfo.offset, endOffset - localTextInfo.offset)
                            )
                        )
                    }
                }
            }
            else -> {
                error("Unexpected property type: ${property.returnType.classifier}")
            }
        }

        textInfo = null
    }
}