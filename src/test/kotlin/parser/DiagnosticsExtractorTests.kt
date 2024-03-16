package parser

import AntlrDiagnostic
import LineColumn
import SourceInterval
import helpers.CustomDiagnosticsHandler
import helpers.DiagnosticInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class DiagnosticsExtractorTests {
    private val baseInput = """
grammar test
/*!UnrecognizedToken!*/`/*!*/
/*!MissingToken!*//*!*//*!ExtraToken!*/+=/*!*/
""".trimIndent()

    private val baseRefinedInput = """
grammar test
`
+=
""".trimIndent()

    @Test
    fun baseExtractDiagnostics() {
        val extractionResult = CustomDiagnosticsHandler.extract(baseInput)

        assertEquals(baseRefinedInput, extractionResult.refinedInput)

        val lineIndexes = baseRefinedInput.getLineIndexes()

        assertEquals(
            DiagnosticInfo(
                "UnrecognizedToken", listOf(),
                SourceInterval(LineColumn(2, 1).getOffset(lineIndexes), 1)
            ),
            extractionResult.diagnostics[0],
        )
        checkDiagnostic(
            DiagnosticInfo(
                "MissingToken", listOf(),
                SourceInterval(LineColumn(3, 1).getOffset(lineIndexes), 0)
            ),
            extractionResult.diagnostics[1],
        )
        checkDiagnostic(
            DiagnosticInfo(
                "ExtraToken", listOf(),
                SourceInterval(LineColumn(3, 1).getOffset(lineIndexes), 2)
            ),
            extractionResult.diagnostics[2],
        )
    }

    @Test
    fun baseEmbedDiagnostics() {
        val actualDiagnostics = mutableListOf<AntlrDiagnostic>()
        val lexer = AntlrLexer(baseRefinedInput) { actualDiagnostics.add(it) }
        val parser = AntlrParser(AntlrLexerTokenStream(lexer)) { actualDiagnostics.add(it) }
        parser.parseGrammar()

        assertEquals(baseInput, CustomDiagnosticsHandler.embed(baseRefinedInput, actualDiagnostics))
    }

    @Test
    fun diagnosticInStringLiteral() {
        val input = """
grammar test;
a : '/*!InvalidEscaping!*/\u/*!*/';
        """.trimIndent()

        val refinedInput = """
grammar test;
a : '\u';
        """.trimIndent()

        val (_, actualRefinedInput) = CustomDiagnosticsHandler.extract(input)

        assertEquals(refinedInput, actualRefinedInput)

        val actualDiagnostics = buildList {
            val lexer = AntlrLexer(refinedInput) { add(it) }
            AntlrParser(AntlrLexerTokenStream(lexer)) { add(it) }.parseGrammar()
        }

        val actualInput = CustomDiagnosticsHandler.embed(actualRefinedInput, actualDiagnostics)

        assertEquals(input, actualInput)
    }

    @Test
    fun unclosedDiagnosticDescriptor() {
        val exception = assertThrows<IllegalStateException> { CustomDiagnosticsHandler.extract("""
grammar test /*!UnrecognizedToken!*/`
        """.trimIndent())
        }
        assertEquals("Unclosed diagnostic descriptor `UnrecognizedToken` at 1:14", exception.message)
    }

    @Test
    fun unexpectedDiagnosticEndMarker() {
        val exception = assertThrows<IllegalStateException> { CustomDiagnosticsHandler.extract("""
grammar test /*!UnrecognizedToken!*/`/*!*//*!*/
        """.trimIndent())
        }
        assertEquals("Unexpected diagnostic end marker at 1:43", exception.message)
    }

    private fun checkDiagnostic(expectedDiagnosticInfo: DiagnosticInfo, actualDiagnosticInfo: DiagnosticInfo) {
        assertEquals(expectedDiagnosticInfo.name, actualDiagnosticInfo.name)
        // TODO: comparison of args
        assertEquals(expectedDiagnosticInfo.location, actualDiagnosticInfo.location)
    }
}