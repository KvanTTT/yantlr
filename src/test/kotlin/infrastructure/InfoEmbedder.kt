package infrastructure

import Diagnostic
import SourceInterval
import infrastructure.testDescriptors.TextPropertyValue
import infrastructure.testDescriptors.TextType
import parser.AntlrNode
import parser.getLineColumn
import parser.getLineOffsetsAndMainLineBreak
import parser.stringEscapeToLiteralChars
import semantics.Mode
import semantics.Rule
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

object InfoEmbedder {
    fun embedDiagnostics(extractionResult: ExtractionResult, embeddedInfos: List<Diagnostic>): String {
        return embed(extractionResult, embeddedInfos.map { it.toInfoWithDescriptor() })
    }

    private data class InfoWithOffset(val infoWithDescriptor: InfoWithDescriptor<*>, val start: Boolean)

    fun embed(extractionResult: ExtractionResult, embeddedInfos: List<InfoWithDescriptor<*>>): String {
        if (embeddedInfos.isEmpty()) return extractionResult.refinedInput

        val offsetToInfoMap = sortedMapOf<Int, MutableList<InfoWithOffset>>()

        fun getInfosWithOffset(offset: Int): MutableList<InfoWithOffset> =
            offsetToInfoMap[offset] ?: run {
                mutableListOf<InfoWithOffset>().also { offsetToInfoMap[offset] = it }
            }

        for (info in embeddedInfos.sortedBy { it.info.sourceInterval.offset }) {
            val interval = info.info.sourceInterval
            getInfosWithOffset(interval.offset).add(InfoWithOffset(info, start = true))
            if (info.descriptor is DiagnosticInfoDescriptor<*>) {
                getInfosWithOffset(interval.end()).add(InfoWithOffset(info, start = false))
            }
        }

        val input = extractionResult.refinedInput
        val lineOffsetsAndMainLineBreak by lazy(LazyThreadSafetyMode.NONE) {
            input.getLineOffsetsAndMainLineBreak()
        }

        var lastOffset = 0
        return buildString {
            for ((offset, infosWithOffset) in offsetToInfoMap) {
                append(input.subSequence(lastOffset, offset))

                for (infoWithOffset in infosWithOffset) {
                    val (info, descriptor) = infoWithOffset.infoWithDescriptor

                    if (descriptor is DiagnosticInfoDescriptor<*>) {
                        append(descriptor.startMarker)
                        if (infoWithOffset.start) {
                            when (info) {
                                is Diagnostic -> {
                                    val actualDiagnosticsName = info::class.simpleName!!.removeSuffix("Diagnostic")
                                    append(actualDiagnosticsName)

                                    val expectedDiagnostic =
                                        extractionResult.diagnostics[offset]?.single { it.name == actualDiagnosticsName }
                                    if (expectedDiagnostic?.args != null) {
                                        appendDiagnosticArgs(info, descriptor, lineOffsetsAndMainLineBreak.lineOffsets)
                                    }
                                }
                                is DiagnosticInfo -> {
                                    append(info.name)
                                    if (info.args != null) {
                                        for (arg in info.args) {
                                            append(' ')
                                            append(arg)
                                        }
                                    }
                                }
                                else -> {
                                    error("Unexpected info type: $info")
                                }
                            }
                        }
                        append(descriptor.endMarker)
                        lastOffset = offset
                    } else if (descriptor == DumpInfoDescriptor) {
                        info as DumpInfo
                        val lineBreak = lineOffsetsAndMainLineBreak.lineBreak
                        val isCodeType = (info.propertyValue as? TextPropertyValue)?.textType == TextType.Code

                        if (!isCodeType) {
                            ensureBackTwoLineBreaks(lineBreak)
                            append("```")
                            append(info.format)
                            append(lineBreak)
                        }

                        append(info.getDump(lineBreak))

                        if (!isCodeType) {
                            append(lineBreak)
                            append("```")
                            ensureNextTwoLineBreaks(lineBreak, input, info.sourceInterval.end())
                        }

                        lastOffset = info.sourceInterval.end()
                    }
                }
            }

            append(input.subSequence(lastOffset, input.length))
        }
    }

    private fun StringBuilder.appendDiagnosticArgs(
        diagnostic: Diagnostic, descriptor: DiagnosticInfoDescriptor<*>, lineOffsets: List<Int>
    ) {
        val nameToPropertyMap = diagnostic::class.memberProperties.associateBy { it.name }
        for (member in diagnostic::class.primaryConstructor!!.parameters) {
            @Suppress("UNCHECKED_CAST")
            val property = nameToPropertyMap[member.name] as KProperty1<Any, *>
            if (member.name.let { it == "sourceInterval" } || descriptor.ignoredPropertyNames.contains(member.name)) continue

            append(' ')

            val normalizedValue = when (val value = property.get(diagnostic)) {
                is Mode -> value.name
                is Rule -> value.name
                is AntlrNode -> value.getInterval().offset.getLineColumn(lineOffsets)
                is SourceInterval -> value.offset.getLineColumn(lineOffsets)
                else -> value
            }
            val valueString = normalizedValue.toString()
            if (valueString.any { stringEscapeToLiteralChars.containsKey(it) }) {
                append('"')
                valueString.forEach { char ->
                    stringEscapeToLiteralChars[char]?.let { append('\\').append(it) } ?: append(char)
                }
                append('"')
            } else {
                append(valueString)
            }
        }
    }

    private fun StringBuilder.ensureBackTwoLineBreaks(lineBreak: String) {
        if (isEmpty()) return

        var lineBreaksCount = 0
        var index = length - 1

        fun checkAndDecrement(): Boolean {
            if (index < 0) return false

            when (get(index)) {
                '\r' -> {
                    lineBreaksCount++
                    index--
                    return true
                }
                '\n' -> {
                    lineBreaksCount++
                    index--
                    if (index >= 0 && get(index) == '\r') {
                        index--
                    }
                    return true
                }
            }

            return false
        }

        if (checkAndDecrement()) {
            checkAndDecrement()
        }

        for (i in 0..<2 - lineBreaksCount) {
            append(lineBreak)
        }
    }

    private fun StringBuilder.ensureNextTwoLineBreaks(lineBreak: String, input: String, offset: Int) {
        if (offset >= input.length) return

        var lineBreaksCount = 0
        var index = offset

        fun checkAndIncrement(): Boolean {
            if (index >= input.length) return false

            when (input[index]) {
                '\r' -> {
                    lineBreaksCount++
                    index++
                    if (index < input.length && input[index] == '\n') {
                        index++
                    }
                    return true
                }
                '\n' -> {
                    lineBreaksCount++
                    index++
                    return true
                }
            }

            return false
        }

        if (checkAndIncrement()) {
            checkAndIncrement()
        }

        for (i in 0..<2 - lineBreaksCount) {
            append(lineBreak)
        }
    }
}