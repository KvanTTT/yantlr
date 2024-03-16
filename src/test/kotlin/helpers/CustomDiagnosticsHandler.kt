package helpers

import AntlrDiagnostic
import SourceInterval
import parser.getLineColumn
import parser.getLineIndexes

object CustomDiagnosticsHandler {
    private const val DIAGNOSTIC_NAME_MARKER = "diagnosticName"
    private const val DIAGNOSTIC_BEGIN_MARKER = "/*!"
    private const val DIAGNOSTIC_START_END_MARKER = "!*/"
    private const val DIAGNOSTIC_END_END = "*/"

    private val markerRegex = Regex(
        """${Regex.escape(DIAGNOSTIC_BEGIN_MARKER)}(${Regex.escape(DIAGNOSTIC_END_END)}|(?<$DIAGNOSTIC_NAME_MARKER>.+?)${
            Regex.escape(DIAGNOSTIC_START_END_MARKER)
        })"""
    )

    private data class DescriptorStart(val name: String, val offset: Int, val refinedOffset: Int)

    data class ExtractionResult(val diagnostics: List<DiagnosticInfo>, val refinedInput: String)

    fun extract(input: String): ExtractionResult {
        var offset = 0
        val descriptorStartStack = ArrayDeque<DescriptorStart>()
        val diagnosticInfos = mutableListOf<DiagnosticInfo>()

        val lineIndexes = input.getLineIndexes()

        val refinedInput = buildString {
            while (true) {
                val match = markerRegex.find(input, offset) ?: break

                val first = match.range.first
                append(input.subSequence(offset, first))

                val diagnosticName = match.groups[DIAGNOSTIC_NAME_MARKER]
                if (diagnosticName != null) {
                    descriptorStartStack.add(DescriptorStart(diagnosticName.value, first, length))
                } else {
                    val lastDescriptorStart = descriptorStartStack.removeLastOrNull()
                        ?: error("Unexpected diagnostic end marker at ${lineIndexes.getLineColumn(first)}")
                    val location =
                        SourceInterval(lastDescriptorStart.refinedOffset, length - lastDescriptorStart.refinedOffset)
                    // TODO: initialize args
                    diagnosticInfos.add(DiagnosticInfo(lastDescriptorStart.name, listOf(), location))
                }

                offset = match.range.last + 1
            }

            append(input.subSequence(offset, input.length))
        }

        for (diagnosticStart in descriptorStartStack) {
            error(
                "Unclosed diagnostic descriptor `${diagnosticStart.name}` at ${
                    lineIndexes.getLineColumn(
                        diagnosticStart.offset
                    )
                }"
            )
        }

        return ExtractionResult(diagnosticInfos, refinedInput)
    }

    fun embed(input: String, diagnostics: List<AntlrDiagnostic>): String {
        data class DiagnosticInfo(val diagnostic: AntlrDiagnostic, val start: Boolean)

        val offsetToDiagnosticMap = linkedMapOf<Int, MutableList<DiagnosticInfo>>()

        fun getDiagnosticsInfos(offset: Int): MutableList<DiagnosticInfo> =
            offsetToDiagnosticMap[offset] ?: run {
                mutableListOf<DiagnosticInfo>().also { offsetToDiagnosticMap[offset] = it }
            }

        for (diagnostic in diagnostics.sortedBy { it.sourceInterval.offset }) {
            val interval = diagnostic.sourceInterval
            getDiagnosticsInfos(interval.offset).add(DiagnosticInfo(diagnostic, start = true))
            getDiagnosticsInfos(interval.end()).add(DiagnosticInfo(diagnostic, start = false))
        }

        var lastOffset = 0
        return buildString {
            for ((offset, diagnosticInfos) in offsetToDiagnosticMap) {
                append(input.subSequence(lastOffset, offset))

                for (diagnosticInfo in diagnosticInfos) {
                    append(DIAGNOSTIC_BEGIN_MARKER)
                    if (diagnosticInfo.start) {
                        append(diagnosticInfo.diagnostic::class.simpleName)
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
}