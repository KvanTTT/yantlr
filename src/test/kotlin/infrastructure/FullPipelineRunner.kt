package infrastructure

import AntlrDiagnostic
import GrammarPipeline
import com.intellij.rt.execution.junit.FileComparisonFailure
import infrastructure.testDescriptors.TestDescriptorDiagnostic
import infrastructure.testDescriptors.TestDescriptorExtractor
import java.io.File

object FullPipelineRunner {
    fun run(file: File) {
        val content = file.readText()
        when (file.extension) {
            "g4" -> {
                val expectedGrammarDiagnosticsInfos = mutableMapOf<Int, List<DiagnosticInfo>>()
                val actualGrammarDiagnostics = mutableListOf<AntlrDiagnostic>()

                val extractionResult = runGrammar(content, 0, expectedGrammarDiagnosticsInfos, actualGrammarDiagnostics)

                val grammarWithActualDiagnostics = InfoEmbedder.embedDiagnostics(extractionResult, actualGrammarDiagnostics)

                failFileComparisonIfNotEqual(
                    "Grammar diagnostics are not equal", content, grammarWithActualDiagnostics, file)
            }
            "md" -> {
                val testDescriptorExtractionResult = TestDescriptorDiagnosticsExtractor.extract(content)

                val testDescriptorDiagnostics = mutableListOf<TestDescriptorDiagnostic>()
                val testDescriptor = TestDescriptorExtractor.extract(testDescriptorExtractionResult.refinedInput, file.nameWithoutExtension) {
                    testDescriptorDiagnostics.add(it)
                }

                val inputWithEmbeddedDiagnostics = InfoEmbedder.embedDiagnostics(testDescriptorExtractionResult, testDescriptorDiagnostics)

                failFileComparisonIfNotEqual(
                    "Test descriptor diagnostics are not equal", content, inputWithEmbeddedDiagnostics, file)

                if (testDescriptorDiagnostics.isNotEmpty()) {
                    throw FileComparisonFailure(
                        "Test descriptor contains errors",
                        testDescriptorExtractionResult.refinedInput,
                        inputWithEmbeddedDiagnostics,
                        file.path
                    )
                }

                val grammarDiagnostics = mutableListOf<AntlrDiagnostic>()
                val expectedGrammarDiagnostics = mutableMapOf<Int, List<DiagnosticInfo>>()

                for (grammar in testDescriptor.grammars) {
                    runGrammar(grammar.value, grammar.sourceInterval.offset, expectedGrammarDiagnostics, grammarDiagnostics)
                }

                val (_, refinedInput) = AntlrDiagnosticsExtractor.extract(testDescriptorExtractionResult.refinedInput)

                val antlrDiagnosticsExtractionResult = ExtractionResult(expectedGrammarDiagnostics, refinedInput)
                val testDescriptorWithActualDiagnostics =
                    InfoEmbedder.embedDiagnostics(antlrDiagnosticsExtractionResult, grammarDiagnostics)

                failFileComparisonIfNotEqual(
                    "Grammar diagnostics are not equal", content, testDescriptorWithActualDiagnostics, file)
            }
            else -> error("Valid extensions are `g4` and `md`")
        }
    }

    private fun failFileComparisonIfNotEqual(message: String, expected: String, actual: String, file: File) {
        if (expected != actual) {
            throw FileComparisonFailure(message, expected, actual, file.path)
        }
    }

    private fun runGrammar(
        grammar: CharSequence,
        grammarOffset: Int,
        expectedGrammarDiagnostics: MutableMap<Int, List<DiagnosticInfo>>,
        grammarDiagnostics: MutableList<AntlrDiagnostic>,
    ) : ExtractionResult {
        val grammarDiagnosticsInfo = AntlrDiagnosticsExtractor.extract(grammar, grammarOffset)

        expectedGrammarDiagnostics.putAll(grammarDiagnosticsInfo.diagnostics)

        GrammarPipeline.process(grammarDiagnosticsInfo.refinedInput, grammarOffset) {
            grammarDiagnostics.add(it)
        }

        return grammarDiagnosticsInfo
    }
}