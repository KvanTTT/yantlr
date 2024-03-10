package parser

import AntlrDiagnostic
import ExtraToken
import UnrecognizedToken
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class DiagnosticsExtractorTests {
    @Test
    fun simple() {
        val extractionResult = AntlrGrammarDiagnosticsHandler().extract("""
grammar test
/*!UnrecognizedToken!*/`/*!*/
/*!ExtraToken!*/+=/*!*/
        """.trimIndent())

        val actualRefinedInput = extractionResult.refinedTokens.joinToString("") { it.value ?: "" }

        val expectedRefinedInput = """
grammar test
`
+=
""".trimIndent()

        assertEquals(expectedRefinedInput, actualRefinedInput)

        val lexerOnRefinedInput = AntlrLexer(expectedRefinedInput)
        AntlrLexerTokenStream(lexerOnRefinedInput).fetchAllTokens()

        checkDiagnostic(
            UnrecognizedToken(
                AntlrToken(AntlrTokenType.Error, channel = AntlrTokenChannel.Error, value = "`"),
                lexerOnRefinedInput.getOffset(LineColumn(2, 1)), 1
            ),
            extractionResult.diagnostics[0],
            lexerOnRefinedInput
        )

        checkDiagnostic(
            ExtraToken(
                AntlrToken(AntlrTokenType.PlusAssign, channel = AntlrTokenChannel.Default, value = "+="),
                lexerOnRefinedInput.getOffset(LineColumn(3, 1)), 2
            ),
            extractionResult.diagnostics[1],
            lexerOnRefinedInput
        )
    }

    @Test
    fun unknownDiagnosticDescriptor() {
        val exception = assertThrows<IllegalStateException> { AntlrGrammarDiagnosticsHandler().extract("""
grammar test /*!IncorrectDescriptor!*/`/*!*/
        """.trimIndent())
        }
        assertEquals("Unknown diagnostic type `IncorrectDescriptor` at 1:14", exception.message)
    }

    @Test
    fun unclosedDiagnosticDescriptor() {
        val exception = assertThrows<IllegalStateException> { AntlrGrammarDiagnosticsHandler().extract("""
grammar test /*!UnrecognizedToken!*/`
        """.trimIndent())
        }
        assertEquals("Unclosed diagnostic descriptor `UnrecognizedToken` at 1:14", exception.message)
    }

    @Test
    fun unexpectedDiagnosticEndMarker() {
        val exception = assertThrows<IllegalStateException> { AntlrGrammarDiagnosticsHandler().extract("""
grammar test /*!UnrecognizedToken!*/`/*!*//*!*/
        """.trimIndent())
        }
        assertEquals("Unexpected diagnostic end marker at 1:43", exception.message)
    }

    private fun checkDiagnostic(expectedDiagnostic: AntlrDiagnostic, actualDiagnostic: AntlrDiagnostic, lexer: AntlrLexer) {
        assertEquals(expectedDiagnostic::class, actualDiagnostic::class)

        when (expectedDiagnostic) {
            is UnrecognizedToken -> AntlrTreeComparer(lexer).compareToken(expectedDiagnostic.token, (actualDiagnostic as UnrecognizedToken).token)
            is ExtraToken -> AntlrTreeComparer(lexer).compareToken(expectedDiagnostic.token, (actualDiagnostic as ExtraToken).token)
        }

        assertEquals(expectedDiagnostic.stage, actualDiagnostic.stage)
        assertEquals(expectedDiagnostic.severity, actualDiagnostic.severity)
        assertEquals(expectedDiagnostic.start, actualDiagnostic.start)
        assertEquals(expectedDiagnostic.length, actualDiagnostic.length)
    }
}