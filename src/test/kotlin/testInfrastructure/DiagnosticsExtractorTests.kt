package testInfrastructure

import AntlrDiagnostic
import LineColumn
import SourceInterval
import helpers.CustomDiagnosticsHandler
import helpers.DiagnosticInfo
import helpers.ExtractionResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import parser.*
import kotlin.test.Test

class DiagnosticsExtractorTests {
    private val baseInput = """
grammar test
/*❗UnrecognizedToken*/`/*❗*/
/*❗MissingToken*//*❗*//*❗ExtraToken*/+=/*❗*/
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

        val lineOffsets = baseRefinedInput.getLineOffsets()

        val diagnostics = extractionResult.diagnostics.flatMap { it.value }

        assertEquals(
            DiagnosticInfo(
                "UnrecognizedToken", null,
                SourceInterval(LineColumn(2, 1).getOffset(lineOffsets), 1)
            ),
            diagnostics[0],
        )
        checkDiagnostic(
            DiagnosticInfo(
                "MissingToken", null,
                SourceInterval(LineColumn(3, 1).getOffset(lineOffsets), 0)
            ),
            diagnostics[1],
        )
        checkDiagnostic(
            DiagnosticInfo(
                "ExtraToken", null,
                SourceInterval(LineColumn(3, 1).getOffset(lineOffsets), 2)
            ),
            diagnostics[2],
        )
    }

    @Test
    fun baseEmbedDiagnostics() {
        val actualDiagnostics = mutableListOf<AntlrDiagnostic>()
        val lexer = AntlrLexer(baseRefinedInput) { actualDiagnostics.add(it) }
        val parser = AntlrParser(AntlrLexerTokenStream(lexer)) { actualDiagnostics.add(it) }
        parser.parseGrammar()

        assertEquals(baseInput, CustomDiagnosticsHandler.embed(
            ExtractionResult(emptyMap(), baseRefinedInput), actualDiagnostics))
    }

    @Test
    fun diagnosticInStringLiteral() {
        val input = """
grammar test;
a : '/*❗InvalidEscaping*/\u/*❗*/';
        """.trimIndent()

        val refinedInput = """
grammar test;
a : '\u';
        """.trimIndent()

        val extractionResult = CustomDiagnosticsHandler.extract(input)

        assertEquals(refinedInput, extractionResult.refinedInput)

        val actualDiagnostics = buildList {
            val lexer = AntlrLexer(refinedInput) { add(it) }
            AntlrParser(AntlrLexerTokenStream(lexer)) { add(it) }.parseGrammar()
        }

        val actualInput = CustomDiagnosticsHandler.embed(extractionResult, actualDiagnostics)

        assertEquals(input, actualInput)
    }

    @Test
    fun unclosedDiagnosticDescriptor() {
        val exception = assertThrows<IllegalStateException> { CustomDiagnosticsHandler.extract("""
grammar test /*❗UnrecognizedToken*/`
        """.trimIndent())
        }
        assertEquals("Unclosed diagnostic descriptor `UnrecognizedToken` at 1:14", exception.message)
    }

    @Test
    fun unexpectedDiagnosticEndMarker() {
        val exception = assertThrows<IllegalStateException> { CustomDiagnosticsHandler.extract("""
grammar test /*❗UnrecognizedToken*/`/*❗*//*❗*/
        """.trimIndent())
        }
        assertEquals("Unexpected diagnostic end marker at 1:42", exception.message)
    }

    private fun checkDiagnostic(expectedDiagnosticInfo: DiagnosticInfo, actualDiagnosticInfo: DiagnosticInfo) {
        assertEquals(expectedDiagnosticInfo.name, actualDiagnosticInfo.name)
        // TODO: comparison of args
        assertEquals(expectedDiagnosticInfo.location, actualDiagnosticInfo.location)
    }
}