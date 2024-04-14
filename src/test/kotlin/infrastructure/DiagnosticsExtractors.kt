package infrastructure

import AntlrDiagnostic
import Diagnostic
import SourceInterval
import infrastructure.testDescriptors.TestDescriptorDiagnostic
import parser.getLineColumn
import parser.getLineOffsets
import parser.stringLiteralToEscapeChars

data class DiagnosticInfo(val name: String, val args: List<String>?, val location: SourceInterval)

data class ExtractionResult(val diagnostics: Map<Int, List<DiagnosticInfo>>, val refinedInput: String)

object AntlrDiagnosticsExtractor : DiagnosticsExtractor<AntlrDiagnostic>(AntlrDiagnosticInfoDescriptor)

object TestDescriptorDiagnosticsExtractor : DiagnosticsExtractor<TestDescriptorDiagnostic>(TestDescriptorDiagnosticInfoDescriptor)

abstract class DiagnosticsExtractor<T : Diagnostic>(diagnosticInfoDescriptor: DiagnosticInfoDescriptor<T>) {
    companion object {
        private const val DIAGNOSTIC_NAME_MARKER = "diagnosticName"
        private const val DIAGNOSTIC_ARGS = "args"
    }

    private val markerRegex = Regex(
        """${Regex.escape(diagnosticInfoDescriptor.startMarker)}(${Regex.escape(diagnosticInfoDescriptor.endMarker)}|((?<$DIAGNOSTIC_NAME_MARKER>\w+)(?<$DIAGNOSTIC_ARGS>.*?))${
            Regex.escape(diagnosticInfoDescriptor.endMarker)
        })"""
    )

    private data class DescriptorStart(val name: String, val args: List<String>?, val offset: Int, val refinedOffset: Int)

    fun extract(input: CharSequence, inputOffset: Int = 0): ExtractionResult {
        var offset = 0
        val descriptorStartStack = ArrayDeque<DescriptorStart>()
        val diagnosticInfos = linkedMapOf<Int, MutableList<DiagnosticInfo>>()

        val lineOffsets by lazy(LazyThreadSafetyMode.NONE) { input.getLineOffsets() }

        val refinedInput = buildString {
            while (true) {
                val match = markerRegex.find(input, offset) ?: break

                val first = match.range.first
                append(input.subSequence(offset, first))

                val diagnosticName = match.groups[DIAGNOSTIC_NAME_MARKER]
                if (diagnosticName != null) {
                    descriptorStartStack.add(DescriptorStart(diagnosticName.value, parseArgs(match.groups[DIAGNOSTIC_ARGS]?.value), first, length))
                } else {
                    val lastDescriptorStart = descriptorStartStack.removeLastOrNull()
                        ?: error("Unexpected diagnostic end marker at ${first.getLineColumn(lineOffsets)}")
                    val diagnosticInfoOffset = lastDescriptorStart.refinedOffset + inputOffset
                    val list = diagnosticInfos.getOrPut(diagnosticInfoOffset) { mutableListOf() }
                    val location =
                        SourceInterval(diagnosticInfoOffset, length - lastDescriptorStart.refinedOffset)
                    list.add(DiagnosticInfo(lastDescriptorStart.name, lastDescriptorStart.args, location))
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