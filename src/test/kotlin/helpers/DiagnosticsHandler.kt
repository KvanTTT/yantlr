package helpers

import Diagnostic
import SourceInterval
import parser.getLineColumn
import parser.getLineOffsets
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

abstract class DiagnosticsHandler<T : Diagnostic>(
    private val diagnosticStartMarker: String,
    private val diagnosticEndMarker: String,
    private val getDiagnosticName: (T) -> String,
    private val ignoredPropertyNames: Set<String>,
) {
    companion object {
        private const val DIAGNOSTIC_NAME_MARKER = "diagnosticName"
        private const val DIAGNOSTIC_ARGS = "args"
    }

    private val markerRegex = Regex(
        """${Regex.escape(diagnosticStartMarker)}(${Regex.escape(diagnosticEndMarker)}|((?<$DIAGNOSTIC_NAME_MARKER>\w+)(?<$DIAGNOSTIC_ARGS>.*?))${
            Regex.escape(diagnosticEndMarker)
        })"""
    )

    private data class DescriptorStart(val name: String, val args: List<String>?, val offset: Int, val refinedOffset: Int)

    fun extract(input: String): ExtractionResult {
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
                    val location =
                        SourceInterval(lastDescriptorStart.refinedOffset, length - lastDescriptorStart.refinedOffset)
                    val list = diagnosticInfos.getOrPut(lastDescriptorStart.refinedOffset) { mutableListOf() }
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
                                    str.elementAtOrNull(offset + 1)?.let { append(it) }
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

    fun embed(extractionResult: ExtractionResult, actualDiagnostics: List<T>): String {
        data class DiagnosticInfo(val diagnostic: T, val start: Boolean)

        val offsetToDiagnosticMap = linkedMapOf<Int, MutableList<DiagnosticInfo>>()

        fun getDiagnosticsInfos(offset: Int): MutableList<DiagnosticInfo> =
            offsetToDiagnosticMap[offset] ?: run {
                mutableListOf<DiagnosticInfo>().also { offsetToDiagnosticMap[offset] = it }
            }

        for (diagnostic in actualDiagnostics.sortedBy { it.sourceInterval.offset }) {
            val interval = diagnostic.sourceInterval
            getDiagnosticsInfos(interval.offset).add(DiagnosticInfo(diagnostic, start = true))
            getDiagnosticsInfos(interval.end()).add(DiagnosticInfo(diagnostic, start = false))
        }

        val input = extractionResult.refinedInput
        val lineOffsets by lazy(LazyThreadSafetyMode.NONE) { input.getLineOffsets() }

        var lastOffset = 0
        return buildString {
            for ((offset, diagnosticInfos) in offsetToDiagnosticMap) {
                append(input.subSequence(lastOffset, offset))

                for (diagnosticInfo in diagnosticInfos) {
                    append(diagnosticStartMarker)
                    if (diagnosticInfo.start) {
                        val actualDiagnosticsName = getDiagnosticName(diagnosticInfo.diagnostic)
                        append(actualDiagnosticsName)

                        val expectedDiagnostic = extractionResult.diagnostics[offset]?.single { it.name == actualDiagnosticsName }
                        if (expectedDiagnostic?.args != null) {
                            appendArgs(diagnosticInfo.diagnostic, lineOffsets)
                        }
                    }
                    append(diagnosticEndMarker)
                }

                lastOffset = offset
            }

            append(input.subSequence(lastOffset, input.length))
        }
    }

    private fun StringBuilder.appendArgs(diagnostic: T, lineOffsets: List<Int>) {
        val nameToPropertyMap = diagnostic::class.memberProperties.associateBy { it.name }
        for (member in diagnostic::class.primaryConstructor!!.parameters) {
            @Suppress("UNCHECKED_CAST")
            val property = nameToPropertyMap[member.name] as KProperty1<Any, *>
            if (member.name.let { it == "sourceInterval" } || ignoredPropertyNames.contains(member.name)) continue

            append(' ')

            val normalizedValue = when (val value = property.get(diagnostic)) {
                is SourceInterval -> value.offset.getLineColumn(lineOffsets)
                else -> value
            }
            val valueString = normalizedValue.toString()
            if (valueString.contains('\\') || valueString.contains('"')) {
                append('"')
                valueString.forEach {
                    if (it == '\\' || it == '"') append('\\')
                    append(it)
                }
                append('"')
            } else {
                append(valueString)
            }
        }
    }
}

data class ExtractionResult(val diagnostics: Map<Int, List<DiagnosticInfo>>, val refinedInput: String)