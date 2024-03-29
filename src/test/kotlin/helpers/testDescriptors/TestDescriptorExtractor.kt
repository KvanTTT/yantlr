package helpers.testDescriptors

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
        private val whitespaceChars = lineBreakChars + charArrayOf(' ', '\t')
        private const val NOTES_NAME = "notes"

        fun extract(input: String, name: String, diagnosticReporter: ((TestDescriptorDiagnostic) -> Unit)? = null): TestDescriptor {
            return TestDescriptorExtractor(input, name, diagnosticReporter).extract()
        }
    }

    enum class TextType {
        Paragraph,
        Code,
    }

    private val propertyValues: MutableMap<KProperty1<TestDescriptor, *>, Any?> = mutableMapOf()
    private var property: KProperty1<TestDescriptor, *>? = null
    private var propertyValue: Any? = null
    private var textType: TextType? = null

    fun extract(): TestDescriptor {
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
                        processHeader(lineStartOffset, lineEndOffset)
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

        @Suppress("UNCHECKED_CAST")
        return TestDescriptor(
            name = getPropertyValue("name") as? String ?: name,
            notes = getPropertyValue(NOTES_NAME) as? List<PropertyValue> ?: emptyList(),
            grammars = getPropertyValue("grammars") as? List<PropertyValue> ?: run {
                diagnosticReporter?.invoke(
                    TestDescriptorDiagnostic(
                        TestDescriptorDiagnosticType.MissingProperty,
                        "Grammars",
                        SourceInterval(input.length, 0)
                    )
                )
                emptyList()
            },
            input = getPropertyValue("input") as? List<PropertyValue> ?: emptyList(),
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

    private fun processHeader(lineStart: Int, lineEnd: Int) {
        finalizePreviousProperty()

        val subSequence = input.subSequence(lineStart, lineEnd)
        val headerValueStart = subSequence.indexOfFirst { it != '#' && !it.isWhitespace() }
        val headerValueStop = subSequence.indexOfLast { !whitespaceChars.contains(it) } + 1
        val headerValue = subSequence.subSequence(headerValueStart, headerValueStop).toString()

        val descriptorProperty = testDescriptorProperties[headerValue.lowercase()]

        if (descriptorProperty != null) {
            if (!propertyValues.containsKey(descriptorProperty)) {
                property = descriptorProperty
                propertyValue = null
            } else {
                diagnosticReporter?.invoke(
                    TestDescriptorDiagnostic(
                        TestDescriptorDiagnosticType.DuplicatedProperty,
                        headerValue,
                        SourceInterval(lineStart + headerValueStart, headerValue.length)
                    )
                )
            }
        } else {
            diagnosticReporter?.invoke(
                TestDescriptorDiagnostic(
                    TestDescriptorDiagnosticType.UnknownProperty,
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
    private fun finalizeTextValue(startOffset: Int, endOffset: Int) {
        if (textType == null) return

        val currentProperty = property ?: run {
            // Use `Notes` property for first headless paragraph
            testDescriptorProperties.getValue(NOTES_NAME).also { property = it }
        }

        val currentPropertyValue = TextPropertyValue(input.subSequence(startOffset, endOffset), startOffset)
        when (currentProperty.returnType.classifier) {
            List::class -> {
                val value = (propertyValue ?: run {
                    propertyValue = mutableListOf<PropertyValue>()
                    propertyValue
                }) as MutableList<PropertyValue>
                value.add(currentPropertyValue)
            }
            String::class -> {
                val value = propertyValue as? String
                if (value == null) {
                    propertyValue = currentPropertyValue.value.toString()
                } else {
                    diagnosticReporter?.invoke(
                        TestDescriptorDiagnostic(
                            TestDescriptorDiagnosticType.DuplicatedValue,
                            currentProperty.name,
                            SourceInterval(startOffset, endOffset - startOffset)
                        )
                    )
                }
            }
            else -> {
                error("Unexpected property type: ${currentProperty.returnType.classifier}")
            }
        }

        textType = null
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