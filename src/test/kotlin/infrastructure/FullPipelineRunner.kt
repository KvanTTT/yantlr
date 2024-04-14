package infrastructure

import AntlrDiagnostic
import GrammarPipeline
import GrammarPipelineResult
import com.intellij.rt.execution.junit.FileComparisonFailure
import infrastructure.testDescriptors.TestDescriptor
import infrastructure.testDescriptors.TestDescriptorDiagnostic
import infrastructure.testDescriptors.TestDescriptorExtractor
import java.io.File

object FullPipelineRunner {
    fun run(file: File) {
        val content = file.readText()
        when (file.extension) {
            "g4" -> {
                val extractionResult = AntlrDiagnosticsExtractor.extract(content)
                val actualGrammarDiagnostics = mutableListOf<AntlrDiagnostic>()

                GrammarPipeline.run(extractionResult.refinedInput, 0) {
                    actualGrammarDiagnostics.add(it)
                }

                val grammarWithActualDiagnostics = InfoEmbedder.embedDiagnostics(extractionResult, actualGrammarDiagnostics)

                failFileComparisonIfNotEqual(
                    "Grammar diagnostics are not equal", content, grammarWithActualDiagnostics, file)
            }
            "md" -> {
                val (testDescriptor, refinedInput, diagnostics) = getAndCheckTestDescriptor(content, file)

                val grammarResults = mutableListOf<GrammarPipelineResult>()
                val actualGrammarDiagnostics = mutableListOf<AntlrDiagnostic>()

                for (grammar in testDescriptor.grammars) {
                    val result = GrammarPipeline.run(grammar.value, grammar.sourceInterval.offset) {
                        actualGrammarDiagnostics.add(it)
                    }
                    grammarResults.add(result)
                }

                val expectDiagnosticInfos = diagnostics.groupBy { it.sourceInterval.offset }

                val antlrDiagnosticsExtractionResult = ExtractionResult(expectDiagnosticInfos, refinedInput)
                val testDescriptorWithActualDiagnostics =
                    InfoEmbedder.embedDiagnostics(antlrDiagnosticsExtractionResult, actualGrammarDiagnostics)

                failFileComparisonIfNotEqual(
                    "Grammar diagnostics are not equal", content, testDescriptorWithActualDiagnostics, file)
            }
            else -> error("Valid extensions are `g4` and `md`")
        }
    }

    private fun getAndCheckTestDescriptor(content: String, file: File): TestDescriptorInfo {
        val allDiagnosticsExtractionResult = AllDiagnosticsExtractor.extract(content)
        val refinedInput = allDiagnosticsExtractionResult.refinedInput

        val actualDescriptorDiagnostics = mutableListOf<TestDescriptorDiagnostic>()
        val testDescriptor = TestDescriptorExtractor.extract(refinedInput, file.nameWithoutExtension) {
            actualDescriptorDiagnostics.add(it)
        }

        val notTestDescriptorDiagnostics = allDiagnosticsExtractionResult.diagnostics.flatMap { it.value }
            .filterNot { it.descriptor is TestDescriptorDiagnosticInfoDescriptor }

        val inputWithActualDescriptorDiagnostics =
            InfoEmbedder.embed(allDiagnosticsExtractionResult,
                (actualDescriptorDiagnostics + notTestDescriptorDiagnostics).map { it.toInfoWithDescriptor() }
            )

        failFileComparisonIfNotEqual(
            "Test descriptor diagnostics are not equal", content, inputWithActualDescriptorDiagnostics, file
        )

        if (actualDescriptorDiagnostics.isNotEmpty()) {
            val refinedInputWithoutDescriptorErrors = InfoEmbedder.embed(
                allDiagnosticsExtractionResult,
                notTestDescriptorDiagnostics.map { it.toInfoWithDescriptor() }
            )
            throw FileComparisonFailure(
                "Test descriptor contains errors",
                refinedInputWithoutDescriptorErrors,
                inputWithActualDescriptorDiagnostics,
                file.path
            )
        }

        return TestDescriptorInfo(testDescriptor, refinedInput, notTestDescriptorDiagnostics)
    }

    private data class TestDescriptorInfo(
        val testDescriptor: TestDescriptor,
        val refinedInput: String,
        val regularDiagnosticInfos: List<DiagnosticInfo>,
    )

    private fun failFileComparisonIfNotEqual(message: String, expected: String, actual: String, file: File) {
        if (expected != actual) {
            throw FileComparisonFailure(message, expected, actual, file.path)
        }
    }
}