package parser

import AntlrDiagnostic
import ExtraToken
import LexerDiagnostic
import MissingToken
import SourceInterval
import UnrecognizedToken
import helpers.CustomDiagnosticsHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class DiagnosticsExtractorTests {
    val baseInput = """
grammar test
/*!UnrecognizedToken!*/`/*!*/
/*!MissingToken!*//*!*//*!ExtraToken!*/+=/*!*/
""".trimIndent()

    val baseRefinedInput = """
grammar test
`
+=
""".trimIndent()

    @Test
    fun baseExtractDiagnostics() {
        val extractionResult = CustomDiagnosticsHandler().extract(baseInput)

        assertEquals(baseRefinedInput, extractionResult.refinedInput)

        val lineIndexes = baseRefinedInput.getLineIndexes()

        checkDiagnostic(
            UnrecognizedToken(
                "`",
                SourceInterval(LineColumn(2, 1).getOffset(lineIndexes), 1)
            ),
            extractionResult.diagnostics[0],
        )
        checkDiagnostic(
            MissingToken(SourceInterval(LineColumn(3, 1).getOffset(lineIndexes), 0)),
            extractionResult.diagnostics[1],
        )
        checkDiagnostic(
            ExtraToken(SourceInterval(LineColumn(3, 1).getOffset(lineIndexes), 2)),
            extractionResult.diagnostics[2],
        )
    }

    @Test
    fun baseEmbedDiagnostics() {
        val actualDiagnostics = mutableListOf<AntlrDiagnostic>()
        val lexer = AntlrLexer(baseRefinedInput) { actualDiagnostics.add(it) }
        val parser = AntlrParser(AntlrLexerTokenStream(lexer)) { actualDiagnostics.add(it) }
        parser.parseGrammar()

        assertEquals(baseInput, CustomDiagnosticsHandler().embed(baseRefinedInput, actualDiagnostics))
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

        val handler = CustomDiagnosticsHandler()
        val (_, actualRefinedInput) = handler.extract(input)

        assertEquals(refinedInput, actualRefinedInput)

        val actualDiagnostics = buildList {
            val lexer = AntlrLexer(refinedInput) { add(it) }
            AntlrParser(AntlrLexerTokenStream(lexer)) { add(it) }.parseGrammar()
        }

        val actualInput = handler.embed(actualRefinedInput, actualDiagnostics)

        assertEquals(input, actualInput)
    }

    @Test
    fun unknownDiagnosticDescriptor() {
        val exception = assertThrows<IllegalStateException> { CustomDiagnosticsHandler().extract("""
grammar test /*!IncorrectDescriptor!*/`/*!*/
        """.trimIndent())
        }
        assertEquals("Unknown diagnostic type `IncorrectDescriptor` at 1:14", exception.message)
    }

    @Test
    fun unclosedDiagnosticDescriptor() {
        val exception = assertThrows<IllegalStateException> { CustomDiagnosticsHandler().extract("""
grammar test /*!UnrecognizedToken!*/`
        """.trimIndent())
        }
        assertEquals("Unclosed diagnostic descriptor `UnrecognizedToken` at 1:14", exception.message)
    }

    @Test
    fun unexpectedDiagnosticEndMarker() {
        val exception = assertThrows<IllegalStateException> { CustomDiagnosticsHandler().extract("""
grammar test /*!UnrecognizedToken!*/`/*!*//*!*/
        """.trimIndent())
        }
        assertEquals("Unexpected diagnostic end marker at 1:43", exception.message)
    }

    private fun checkDiagnostic(expectedDiagnostic: AntlrDiagnostic, actualDiagnostic: AntlrDiagnostic) {
        assertEquals(expectedDiagnostic::class, actualDiagnostic::class)

        if (expectedDiagnostic is LexerDiagnostic) {
            assertEquals(expectedDiagnostic.value, (actualDiagnostic as UnrecognizedToken).value)
        }

        assertEquals(expectedDiagnostic.severity, actualDiagnostic.severity)
        assertEquals(expectedDiagnostic.sourceInterval, actualDiagnostic.sourceInterval)
    }
}