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
                val (testDescriptor, refinedInput, diagnosticInfos) = getAndCheckTestDescriptor(content, file)

                val grammarResults = mutableListOf<GrammarPipelineResult>()
                val actualGrammarDiagnostics = mutableListOf<AntlrDiagnostic>()

                for (grammar in testDescriptor.grammars) {
                    val result = GrammarPipeline.run(grammar.value, grammar.sourceInterval.offset) {
                        actualGrammarDiagnostics.add(it)
                    }
                    grammarResults.add(result)
                }

                val expectDiagnosticInfos = diagnosticInfos.groupBy { it.sourceInterval.offset }

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

        val otherDiagnostics = allDiagnosticsExtractionResult.diagnostics.flatMap { it.value }
            .filterNot { it.descriptor is TestDescriptorDiagnosticInfoDescriptor }

        if (actualDescriptorDiagnostics.isNotEmpty()) {
            val otherDiagnosticInfos = otherDiagnostics.map { it.toInfoWithDescriptor() }
            val inputWithoutDescriptorErrors = InfoEmbedder.embed(
                allDiagnosticsExtractionResult,
                otherDiagnosticInfos,
            )
            val inputWithDescriptorErrors = InfoEmbedder.embed(
                allDiagnosticsExtractionResult,
                actualDescriptorDiagnostics.map { it.toInfoWithDescriptor() } + otherDiagnosticInfos
            )
            throw FileComparisonFailure(
                "Test descriptor contains errors",
                inputWithoutDescriptorErrors,
                inputWithDescriptorErrors,
                file.path
            )
        }

        return TestDescriptorInfo(testDescriptor, refinedInput, otherDiagnostics)
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