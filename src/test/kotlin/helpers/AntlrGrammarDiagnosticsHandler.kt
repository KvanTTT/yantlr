package helpers

import AntlrDiagnostic
import ExtraToken
import MissingToken
import UnrecognizedToken
import parser.*
import kotlin.reflect.KClass

class AntlrGrammarDiagnosticsHandler {
    companion object {
        private const val DIAGNOSTIC_START_BEGIN_MARKER = "/*!"
        private const val DIAGNOSTIC_START_END_MARKER = "!*/"
        private const val DIAGNOSTIC_END = "/*!*/"
    }

    private data class DescriptorStart(val type: KClass<*>, val offset: Int, val refinedOffset: Int, val info: List<Any>)

    data class ExtractionResult(val diagnostics: List<AntlrDiagnostic>, val refinedTokens: List<AntlrToken>)

    fun extract(input: String): ExtractionResult {
        val lexer = AntlrLexer(input, initializeTokenValue = true)
        val tokenStream = AntlrLexerTokenStream(lexer)

        val descriptorStartStack = ArrayDeque<DescriptorStart>()

        val diagnosticsList = mutableListOf<AntlrDiagnostic>()
        val refinedTokens = mutableListOf<AntlrToken>()
        var refinedOffset = 0
        var ignoredTokensLength = 0

        var tokenIndex = 0
        do {
            val token = tokenStream.getToken(tokenIndex)
            val tokenValue = lexer.getTokenValue(token)

            var ignoreToken = false
            if (token.type == AntlrTokenType.BlockComment) {
                if (tokenValue.startsWith(DIAGNOSTIC_START_BEGIN_MARKER) && tokenValue.endsWith(
                        DIAGNOSTIC_START_END_MARKER
                    ) &&
                    tokenValue.length > DIAGNOSTIC_START_BEGIN_MARKER.length + DIAGNOSTIC_START_END_MARKER.length
                ) {
                    ignoreToken = true
                    val diagnosticMarker = tokenValue.substring(
                        DIAGNOSTIC_START_BEGIN_MARKER.length,
                        tokenValue.length - DIAGNOSTIC_START_END_MARKER.length
                    )
                    descriptorStartStack.add(
                        initDiagnostic(diagnosticMarker, refinedOffset, tokenStream, tokenIndex, lexer)
                    )
                } else if (tokenValue == DIAGNOSTIC_END) {
                    ignoreToken = true
                    val lastDescriptorStart = descriptorStartStack.removeLastOrNull()
                        ?: error("Unexpected diagnostic end marker at ${lexer.getLineColumn(token.offset)}")
                    diagnosticsList.add(finalizeDiagnostic(lastDescriptorStart, refinedOffset))
                }
            }

            if (!ignoreToken) {
                refinedTokens.add(token.shift(token.offset - ignoredTokensLength))
                refinedOffset += token.length
            } else {
                ignoredTokensLength += token.length
            }

            if (token.type == AntlrTokenType.Eof) {
                break
            }

            tokenIndex++
        } while (true)

        for (diagnosticStart in descriptorStartStack) {
            error("Unclosed diagnostic descriptor `${diagnosticStart.type.simpleName}` at ${lexer.getLineColumn(diagnosticStart.offset)}")
        }

        return ExtractionResult(diagnosticsList, refinedTokens)
    }

    fun embedDiagnostics(refinedTokens: List<AntlrToken>, diagnostics: List<AntlrDiagnostic>): String {
        data class DiagnosticInfo(val diagnostic: AntlrDiagnostic, val start: Boolean)

        val offsetToDiagnosticMap = mutableMapOf<Int, MutableList<DiagnosticInfo>>()

        for (diagnostic in diagnostics.sortedBy { it.offset }) {
            val diagnosticStartList = offsetToDiagnosticMap[diagnostic.offset] ?: run {
                mutableListOf<DiagnosticInfo>().also { offsetToDiagnosticMap[diagnostic.offset] = it }
            }
            diagnosticStartList.add(DiagnosticInfo(diagnostic, start = true))
            val diagnosticEndList = offsetToDiagnosticMap[diagnostic.offset + diagnostic.length] ?: run {
                mutableListOf<DiagnosticInfo>().also { offsetToDiagnosticMap[diagnostic.offset + diagnostic.length] = it }
            }
            diagnosticEndList.add(DiagnosticInfo(diagnostic, start = false))
        }

        return buildString {
            for (token in refinedTokens) {
                val diagnosticInfos = offsetToDiagnosticMap[token.offset] ?: emptyList()
                for ((diagnostic, start) in diagnosticInfos) {
                    if (start) {
                        append(DIAGNOSTIC_START_BEGIN_MARKER)
                        append(diagnostic::class.simpleName)
                        append(DIAGNOSTIC_START_END_MARKER)
                    } else {
                        append(DIAGNOSTIC_END)
                    }
                }
                append(token.value)
            }
        }
    }

    private fun initDiagnostic(
        diagnosticMarker: String,
        refinedOffset: Int,
        tokenStream: AntlrLexerTokenStream,
        tokenIndex: Int,
        lexer: AntlrLexer
    ): DescriptorStart {
        val token = tokenStream.getToken(tokenIndex)
        return when (diagnosticMarker) {
            UnrecognizedToken::class.simpleName -> {
                DescriptorStart(
                    UnrecognizedToken::class,
                    token.offset,
                    refinedOffset,
                    listOf(tokenStream.getToken(tokenIndex + 1))
                )
            }

            ExtraToken::class.simpleName -> {
                DescriptorStart(
                    ExtraToken::class,
                    token.offset,
                    refinedOffset,
                    listOf(tokenStream.getToken(tokenIndex + 1))
                )
            }

            MissingToken::class.simpleName -> {
                DescriptorStart(
                    MissingToken::class,
                    token.offset,
                    refinedOffset,
                    listOf(AntlrToken(AntlrTokenType.Semicolon, token.offset, 0, AntlrTokenChannel.Error, ";"))
                )
            }

            else -> {
                error("Unknown diagnostic type `$diagnosticMarker` at ${lexer.getLineColumn(token.offset)}")
            }
        }
    }

    private fun finalizeDiagnostic(lastDescriptorStart: DescriptorStart, refinedOffset: Int) : AntlrDiagnostic {
        return when (lastDescriptorStart.type) {
            UnrecognizedToken::class -> {
                UnrecognizedToken(
                    (lastDescriptorStart.info[0] as AntlrToken).shift(lastDescriptorStart.refinedOffset),
                    lastDescriptorStart.refinedOffset,
                    refinedOffset - lastDescriptorStart.refinedOffset
                )
            }

            ExtraToken::class -> {
                ExtraToken(
                    (lastDescriptorStart.info[0] as AntlrToken).shift(lastDescriptorStart.refinedOffset),
                    lastDescriptorStart.refinedOffset,
                    refinedOffset - lastDescriptorStart.refinedOffset
                )
            }

            MissingToken::class -> {
                MissingToken(
                    (lastDescriptorStart.info[0] as AntlrToken).shift(lastDescriptorStart.refinedOffset),
                    lastDescriptorStart.refinedOffset,
                    refinedOffset - lastDescriptorStart.refinedOffset
                )
            }

            else -> {
                error("Unknown diagnostic type `${lastDescriptorStart.type.simpleName}`")
            }
        }
    }

    private fun AntlrToken.shift(newOffset: Int): AntlrToken {
        return AntlrToken(type, newOffset, length, channel, value)
    }
}