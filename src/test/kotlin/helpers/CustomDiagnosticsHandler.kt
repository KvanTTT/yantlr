package helpers

import AntlrDiagnostic
import SourceInterval
import parser.getLineColumn
import parser.getLineIndexes
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

object CustomDiagnosticsHandler {
    private const val DIAGNOSTIC_NAME_MARKER = "diagnosticName"
    private const val DIAGNOSTIC_ARGS = "args"
    private const val DIAGNOSTIC_BEGIN_MARKER = "/*❗"
    private const val DIAGNOSTIC_START_END_MARKER = "❗*/"
    private const val DIAGNOSTIC_END_END = "*/"

    private val markerRegex = Regex(
        """${Regex.escape(DIAGNOSTIC_BEGIN_MARKER)}(${Regex.escape(DIAGNOSTIC_END_END)}|((?<$DIAGNOSTIC_NAME_MARKER>\w+)(?<$DIAGNOSTIC_ARGS>.*?))${
            Regex.escape(DIAGNOSTIC_START_END_MARKER)
        })"""
    )

    private data class DescriptorStart(val name: String, val args: List<String>?, val offset: Int, val refinedOffset: Int)

    fun extract(input: String): ExtractionResult {
        var offset = 0
        val descriptorStartStack = ArrayDeque<DescriptorStart>()
        val diagnosticInfos = linkedMapOf<Int, MutableList<DiagnosticInfo>>()

        val lineIndexes = input.getLineIndexes()

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
                        ?: error("Unexpected diagnostic end marker at ${first.getLineColumn(lineIndexes)}")
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
                    diagnosticStart.offset.getLineColumn(lineIndexes)
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

    fun embed(extractionResult: ExtractionResult, actualDiagnostics: List<AntlrDiagnostic>): String {
        data class DiagnosticInfo(val diagnostic: AntlrDiagnostic, val start: Boolean)

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
        val lineIndexes = input.getLineIndexes()

        var lastOffset = 0
        return buildString {
            for ((offset, diagnosticInfos) in offsetToDiagnosticMap) {
                append(input.subSequence(lastOffset, offset))

                for (diagnosticInfo in diagnosticInfos) {
                    append(DIAGNOSTIC_BEGIN_MARKER)
                    if (diagnosticInfo.start) {
                        val actualDiagnosticsName = diagnosticInfo.diagnostic::class.simpleName
                        append(actualDiagnosticsName)

                        val expectedDiagnostic = extractionResult.diagnostics[offset]?.single { it.name == actualDiagnosticsName }
                        if (expectedDiagnostic?.args != null) {
                            appendArgs(diagnosticInfo.diagnostic, lineIndexes)
                        }

                        append(DIAGNOSTIC_START_END_MARKER)
                    } else {
                        append(DIAGNOSTIC_END_END)
                    }
                }

                lastOffset = offset
            }

            append(input.subSequence(lastOffset, input.length))
        }
    }

    private fun StringBuilder.appendArgs(diagnostic: AntlrDiagnostic, lineIndexes: List<Int>) {
        val nameToPropertyMap = diagnostic::class.memberProperties.associateBy { it.name }
        for (member in diagnostic::class.primaryConstructor!!.parameters) {
            @Suppress("UNCHECKED_CAST")
            val property = nameToPropertyMap[member.name] as KProperty1<Any, *>
            if (member.name.let { it == "severity" || it == "sourceInterval" }) continue

            append(' ')

            val normalizedValue = when (val value = property.get(diagnostic)) {
                is SourceInterval -> value.offset.getLineColumn(lineIndexes)
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