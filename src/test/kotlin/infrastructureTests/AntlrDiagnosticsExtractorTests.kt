package infrastructureTests

import AntlrDiagnostic
import LineColumn
import SourceInterval
import infrastructure.*
import parser.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

object AntlrDiagnosticsExtractorTests {
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
        val extractionResult = AntlrDiagnosticsExtractor.extract(baseInput)

        assertEquals(baseRefinedInput, extractionResult.refinedInput)

        val lineOffsets = baseRefinedInput.getLineOffsets()

        val diagnostics = extractionResult.diagnostics.flatMap { it.value }

        assertEquals(
            DiagnosticInfo(
                AntlrDiagnosticInfoDescriptor,
                "UnrecognizedToken", null,
                SourceInterval(LineColumn(2, 1).getOffset(lineOffsets), 1)
            ),
            diagnostics[0],
        )
        checkDiagnostic(
            DiagnosticInfo(
                AntlrDiagnosticInfoDescriptor,
                "MissingToken", null,
                SourceInterval(LineColumn(3, 1).getOffset(lineOffsets), 0)
            ),
            diagnostics[1],
        )
        checkDiagnostic(
            DiagnosticInfo(
                AntlrDiagnosticInfoDescriptor,
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

        assertEquals(baseInput, InfoEmbedder.embedDiagnostics(
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

        val extractionResult = AntlrDiagnosticsExtractor.extract(input)

        assertEquals(refinedInput, extractionResult.refinedInput)

        val actualDiagnostics = buildList {
            val lexer = AntlrLexer(refinedInput) { add(it) }
            AntlrParser(AntlrLexerTokenStream(lexer)) { add(it) }.parseGrammar()
        }

        val actualInput = InfoEmbedder.embedDiagnostics(extractionResult, actualDiagnostics)

        assertEquals(input, actualInput)
    }

    @Test
    fun unclosedDiagnosticDescriptor() {
        val exception = assertFailsWith<IllegalStateException> { AntlrDiagnosticsExtractor.extract("""
grammar test /*❗UnrecognizedToken*/`
        """.trimIndent())
        }
        assertEquals("Unclosed diagnostic descriptor `UnrecognizedToken` at 1:14", exception.message)
    }

    @Test
    fun unexpectedDiagnosticEndMarker() {
        val exception = assertFailsWith<IllegalStateException> { AntlrDiagnosticsExtractor.extract("""
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