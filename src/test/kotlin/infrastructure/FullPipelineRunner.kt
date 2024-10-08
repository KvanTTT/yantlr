package infrastructure

import AntlrDiagnostic
import GrammarPipeline
import GrammarPipelineResult
import atn.Atn
import atn.AtnDumper
import com.intellij.rt.execution.junit.FileComparisonFailure
import infrastructure.testDescriptors.TestDescriptor
import infrastructure.testDescriptors.TestDescriptorDiagnostic
import infrastructure.testDescriptors.TestDescriptorExtractor
import types.TypesInfo
import java.io.File
import java.nio.file.Paths
import kotlin.test.junit5.JUnit5Asserter.fail

object FullPipelineRunner {
    fun run(file: File) {
        val content = file.readText()
        when (file.extension) {
            "g4" -> {
                val extractionResult = AntlrDiagnosticsExtractor.extract(content)
                val actualGrammarDiagnostics = mutableListOf<AntlrDiagnostic>()

                runGrammarPipeline(extractionResult.refinedInput, 0, file.nameWithoutExtension, file.parentFile) {
                    actualGrammarDiagnostics.add(it)
                }

                val grammarWithActualDiagnostics = InfoEmbedder.embedDiagnostics(extractionResult, actualGrammarDiagnostics)

                failFileComparisonIfNotEqual(
                    "Grammar diagnostics are not equal", content, grammarWithActualDiagnostics, file)
            }
            "md" -> {
                val (testDescriptor, refinedInput, diagnosticInfos) = getAndCheckTestDescriptor(content, file)

                val grammarResults = mutableListOf<GrammarPipelineResult>()
                val embeddedInfos = mutableListOf<InfoWithDescriptor<*>>()

                for (grammar in testDescriptor.grammars) {
                    grammarResults.add(runGrammarPipeline(grammar.value, grammar.sourceInterval.offset, grammarName = null, file.parentFile) {
                        embeddedInfos.add(it.toInfoWithDescriptor())
                    })
                }

                if (testDescriptor.atn != null) {
                    // TODO: support dump of several grammars
                    val firstGrammarResult = grammarResults.first()
                    embeddedInfos.add(
                        AtnDumpInfo(firstGrammarResult.originalAtn!!, firstGrammarResult.lineOffsets, testDescriptor.atn)
                            .toInfoWithDescriptor()
                    )
                }

                val expectDiagnosticInfos = diagnosticInfos.groupBy { it.sourceInterval.offset }

                val antlrDiagnosticsExtractionResult = ExtractionResult(expectDiagnosticInfos, refinedInput)
                val actual = InfoEmbedder.embed(antlrDiagnosticsExtractionResult, embeddedInfos)

                failFileComparisonIfNotEqual(
                    "Grammar diagnostics or dumps are not equal", content, actual, file)
            }
            else -> error("Valid extensions are `g4` and `md`")
        }
    }

    private fun runGrammarPipeline(
        grammarText: CharSequence,
        grammarOffset: Int,
        grammarName: String?,
        parentFile: File,
        diagnosticReporter: ((AntlrDiagnostic) -> Unit),
    ): GrammarPipelineResult {
        return GrammarPipeline.run(grammarText, grammarOffset, debugMode = true) {
            diagnosticReporter.invoke(it)
        }.also {
            val subdirectory = parentFile.path.substringAfter(resourcesFile.path)
            val dumpName = grammarName ?: it.grammarName
            if (subdirectory.contains("Atn")) {
                dumpAtn(it.originalAtn!!, it.lineOffsets, parentFile, dumpName, minimized = false)
                dumpAtn(it.minimizedAtn, it.lineOffsets, parentFile, dumpName, minimized = true)
            } else if (subdirectory.contains("Types")) {
                dumpTypes(it.typesInfo, it.lineOffsets, parentFile, dumpName)
            }
        }
    }

    private fun dumpAtn(atn: Atn, lineOffsets: List<Int>, parentFile: File, name: String?, minimized: Boolean) {
        val actualAtnDump = AtnDumper(lineOffsets).dump(atn)
        val dumpFile = Paths.get(parentFile.path, "${name}${if (minimized) ".min" else ""}.dot").toFile()
        if (!dumpFile.exists()) {
            dumpFile.writeText(actualAtnDump)
            fail("Expected file doesn't exist. Generating: ${dumpFile.path}")
        } else {
            val expectedAtnDump = dumpFile.readText()
            failFileComparisonIfNotEqual(
                "ATN ${if (minimized) "minimized " else ""} dumps are not equal", expectedAtnDump, actualAtnDump, dumpFile
            )
        }
    }

    private fun dumpTypes(typesInfo: TypesInfo, lineOffsets: List<Int>, parentFile: File, name: String?) {
        val actualDump = TypesDumper(lineOffsets).dump(typesInfo)
        val dumpFile = Paths.get(parentFile.path, "${name}.types").toFile()
        if (!dumpFile.exists()) {
            dumpFile.writeText(actualDump)
            fail("Expected file doesn't exist. Generating: ${dumpFile.path}")
        } else {
            val expectedDump = dumpFile.readText()
            failFileComparisonIfNotEqual(
                "Type dumps are not equal", expectedDump, actualDump, dumpFile
            )
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