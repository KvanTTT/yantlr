package helpers

import AntlrDiagnostic
import ExtraToken
import InvalidEscaping
import MissingToken
import SourceInterval
import UnrecognizedToken
import parser.*
import kotlin.reflect.KClass

class CustomDiagnosticsHandler {
    companion object {
        private const val DIAGNOSTIC_NAME_MARKER = "diagnosticName"
        private const val DIAGNOSTIC_BEGIN_MARKER = "/*!"
        private const val DIAGNOSTIC_START_END_MARKER = "!*/"
        private const val DIAGNOSTIC_END_END = "*/"

        val markerRegex = Regex("""${Regex.escape(DIAGNOSTIC_BEGIN_MARKER)}(${Regex.escape(DIAGNOSTIC_END_END)}|(?<$DIAGNOSTIC_NAME_MARKER>.+?)${Regex.escape(DIAGNOSTIC_START_END_MARKER)})""")
    }

    private data class DescriptorStart(val type: KClass<*>, val offset: Int, val refinedOffset: Int)

    data class ExtractionResult(val diagnostics: List<AntlrDiagnostic>, val refinedInput: String)

    fun extract(input: String): ExtractionResult {
        var offset = 0
        val descriptorStartStack = ArrayDeque<DescriptorStart>()
        val diagnostics = mutableListOf<AntlrDiagnostic>()

        val lineIndexes = input.getLineIndexes()

        val refinedInput = buildString {
            while (true) {
                val match = markerRegex.find(input, offset) ?: break

                val first = match.range.first
                append(input.subSequence(offset, first))

                val diagnosticName = match.groups[DIAGNOSTIC_NAME_MARKER]
                if (diagnosticName != null) {
                    val diagnosticStart = initDiagnostic(diagnosticName.value, first, length) ?:
                        error("Unknown diagnostic type `${diagnosticName.value}` at ${lineIndexes.getLineColumn(first)}")
                    descriptorStartStack.add(diagnosticStart)
                } else {
                    val lastDescriptorStart = descriptorStartStack.removeLastOrNull()
                        ?: error("Unexpected diagnostic end marker at ${lineIndexes.getLineColumn(first)}")
                    diagnostics.add(finalizeDiagnostic(lastDescriptorStart, length, this))
                }

                offset = match.range.last + 1
            }

            append(input.subSequence(offset, input.length))
        }

        for (diagnosticStart in descriptorStartStack) {
            error("Unclosed diagnostic descriptor `${diagnosticStart.type.simpleName}` at ${lineIndexes.getLineColumn(diagnosticStart.offset)}")
        }

        return ExtractionResult(diagnostics, refinedInput)
    }

    fun embed(input: String, diagnostics: List<AntlrDiagnostic>): String {
        data class DiagnosticInfo(val diagnostic: AntlrDiagnostic, val start: Boolean)

        val offsetToDiagnosticMap = linkedMapOf<Int, MutableList<DiagnosticInfo>>()

        for (diagnostic in diagnostics.sortedBy { it.sourceInterval.offset }) {
            val diagnosticStartList = offsetToDiagnosticMap[diagnostic.sourceInterval.offset] ?: run {
                mutableListOf<DiagnosticInfo>().also {
                    offsetToDiagnosticMap[diagnostic.sourceInterval.offset] = it
                }
            }
            diagnosticStartList.add(DiagnosticInfo(diagnostic, start = true))

            val end = diagnostic.sourceInterval.offset + diagnostic.sourceInterval.length
            val diagnosticEndList = offsetToDiagnosticMap[end] ?: run {
                mutableListOf<DiagnosticInfo>().also {
                    offsetToDiagnosticMap[end] = it
                }
            }
            diagnosticEndList.add(DiagnosticInfo(diagnostic, start = false))
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

    private fun initDiagnostic(
        diagnosticMarker: String,
        offset: Int,
        refinedOffset: Int,
    ): DescriptorStart? {
        val type: KClass<*> = when (diagnosticMarker) {
            UnrecognizedToken::class.simpleName -> UnrecognizedToken::class
            InvalidEscaping::class.simpleName -> InvalidEscaping::class
            ExtraToken::class.simpleName -> ExtraToken::class
            MissingToken::class.simpleName -> MissingToken::class
            else -> return null
        }

        return DescriptorStart(type, offset, refinedOffset)
    }

    private fun finalizeDiagnostic(descriptorStart: DescriptorStart, refinedEndOffset: Int, refinedInput: StringBuilder): AntlrDiagnostic {
        val sourceInterval = SourceInterval(descriptorStart.refinedOffset, refinedEndOffset - descriptorStart.refinedOffset)

        return when (descriptorStart.type) {
            UnrecognizedToken::class -> UnrecognizedToken(
                refinedInput.substring(descriptorStart.refinedOffset, refinedEndOffset),
                sourceInterval
            )
            InvalidEscaping::class -> InvalidEscaping(
                refinedInput.substring(descriptorStart.refinedOffset, refinedEndOffset),
                sourceInterval
            )
            ExtraToken::class -> ExtraToken(sourceInterval)
            MissingToken::class -> MissingToken(sourceInterval)
            else -> error("Unknown diagnostic type `${descriptorStart.type}`")
        }
    }
}