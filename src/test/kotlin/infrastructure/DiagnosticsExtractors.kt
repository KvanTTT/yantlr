package infrastructure

import AntlrDiagnostic
import Diagnostic
import InfoWithSourceInterval
import SourceInterval
import infrastructure.testDescriptors.TestDescriptorDiagnostic
import parser.getLineColumn
import parser.getLineOffsets
import parser.stringLiteralToEscapeChars

data class DiagnosticInfo(
    val descriptor: EmbeddedInfoDescriptor<*>,
    val name: String,
    val args: List<String>?,
    val location: SourceInterval
) : InfoWithSourceInterval(location)

data class ExtractionResult(val diagnostics: Map<Int, List<DiagnosticInfo>>, val refinedInput: String)

object AllDiagnosticsExtractor : DiagnosticsExtractor<Diagnostic>(
    listOf(AntlrDiagnosticInfoDescriptor, TestDescriptorDiagnosticInfoDescriptor)
)

object AntlrDiagnosticsExtractor : DiagnosticsExtractor<AntlrDiagnostic>(listOf(AntlrDiagnosticInfoDescriptor))

object TestDescriptorDiagnosticsExtractor : DiagnosticsExtractor<TestDescriptorDiagnostic>(listOf(TestDescriptorDiagnosticInfoDescriptor))

abstract class DiagnosticsExtractor<T : Diagnostic>(private val descriptors: List<DiagnosticInfoDescriptor<out T>>) {
    companion object {
        private const val DESCRIPTOR_MARKER = "descriptor"
        private const val NAME_MARKER = "name"
        private const val ARGS_MARKER = "args"
    }

    private val matchRegex: Regex

    init {
        val descriptorSet = mutableSetOf<String>()
        val regexString = buildString {
            for ((index, descriptor) in descriptors.withIndex()) {
                if (!descriptorSet.add(descriptor.startMarker)) {
                    error("Duplicate descriptor start marker: ${descriptor.startMarker}")
                }

                append(
                    """((?<${DESCRIPTOR_MARKER + index}>${Regex.escape(descriptor.startMarker)})(${
                        Regex.escape(
                            descriptor.endMarker
                        )
                    }|((?<${NAME_MARKER + index}>\w+)(?<${ARGS_MARKER + index}>.*?))${Regex.escape(descriptor.endMarker)}))"""
                )
                if (index < descriptors.size - 1)
                    append('|')
            }
        }

        matchRegex = Regex(regexString)
    }

    private data class DescriptorStart(
        val descriptor: DiagnosticInfoDescriptor<*>,
        val name: String,
        val args: List<String>?,
        val offset: Int,
        val refinedOffset: Int,
    )

    fun extract(input: CharSequence): ExtractionResult {
        var offset = 0
        val descriptorStartStack = ArrayDeque<DescriptorStart>()
        val diagnosticInfos = linkedMapOf<Int, MutableList<DiagnosticInfo>>()

        val lineOffsets by lazy(LazyThreadSafetyMode.NONE) { input.getLineOffsets() }

        val refinedInput = buildString {
            while (true) {
                val match = matchRegex.find(input, offset) ?: break

                val first = match.range.first
                append(input.subSequence(offset, first))

                val matchGroups = match.groups
                val descriptorIndex = descriptors.indices.first { matchGroups[DESCRIPTOR_MARKER + it] != null }

                val diagnosticName = matchGroups[NAME_MARKER + descriptorIndex]
                if (diagnosticName != null) {
                    descriptorStartStack.add(DescriptorStart(
                        descriptors[descriptorIndex],
                        diagnosticName.value,
                        parseArgs(matchGroups[ARGS_MARKER + descriptorIndex]?.value),
                        first,
                        length
                    ))
                } else {
                    val lastDescriptorStart = descriptorStartStack.removeLastOrNull()
                        ?: error("Unexpected diagnostic end marker at ${first.getLineColumn(lineOffsets)}")
                    val diagnosticInfoOffset = lastDescriptorStart.refinedOffset
                    val list = diagnosticInfos.getOrPut(diagnosticInfoOffset) { mutableListOf() }
                    val location =
                        SourceInterval(diagnosticInfoOffset, length - lastDescriptorStart.refinedOffset)
                    list.add(
                        DiagnosticInfo(
                            lastDescriptorStart.descriptor,
                            lastDescriptorStart.name,
                            lastDescriptorStart.args,
                            location
                        )
                    )
                }

                offset = match.range.last + 1
            }

            append(input.subSequence(offset, input.length))
        }

        for (diagnosticStart in descriptorStartStack) {
            error(
                "Unclosed diagnostic descriptor `${diagnosticStart.name}` at ${
                    diagnosticStart.offset.getLineColumn(lineOffsets)
                }"
            )
        }

        return ExtractionResult(diagnosticInfos, refinedInput)
    }

    private fun parseArgs(str: String?): List<String>? {
        if (str.isNullOrEmpty()) return null

        var offset = 0
        val result = mutableListOf<String>()
        while (offset < str.length) {
            when (str[offset]) {
                ' ' -> offset++
                '"' -> {
                    offset++
                    result.add(buildString {
                        while (offset < str.length) {
                            when (str[offset]) {
                                '\\' -> {
                                    str.elementAtOrNull(offset + 1)?.let {
                                        append(stringLiteralToEscapeChars[it] ?: it)
                                    }
                                    offset += 2
                                }
                                '"' -> {
                                    offset++
                                    break
                                }
                                else -> append(str[offset++])
                            }
                        }
                    })
                }
                else -> {
                    val end = str.indexOf(' ', offset).let { if (it == -1) str.length else it }
                    result.add(str.substring(offset, end))
                    offset = end
                }
            }
        }
        return result
    }
}