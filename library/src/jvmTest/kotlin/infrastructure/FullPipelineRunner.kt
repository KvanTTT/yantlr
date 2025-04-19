package infrastructure

import AntlrDiagnostic
import GrammarPipeline
import GrammarPipelineResult
import atn.Atn
import atn.AtnDumper
import infrastructure.testDescriptors.TestDescriptor
import infrastructure.testDescriptors.TestDescriptorDiagnostic
import infrastructure.testDescriptors.TestDescriptorExtractor
import org.junit.jupiter.api.assertAll
import org.opentest4j.AssertionFailedError
import org.opentest4j.FileInfo
import types.TypesInfo
import java.io.File
import java.nio.file.Paths
import kotlin.test.junit5.JUnit5Asserter.fail

object FullPipelineRunner {
    fun run(file: File) {
        val content = file.readText()
        val failExecutables = mutableListOf<() -> Unit>()
        when (file.extension) {
            "g4" -> {
                val extractionResult = AntlrDiagnosticsExtractor.extract(content)
                val actualGrammarDiagnostics = mutableListOf<AntlrDiagnostic>()

                val (_, grammarFailExecutables) = runGrammarPipeline(extractionResult.refinedInput, 0, file.nameWithoutExtension, file.parentFile) {
                    actualGrammarDiagnostics.add(it)
                }
                failExecutables.addAll(grammarFailExecutables)

                val grammarWithActualDiagnostics = InfoEmbedder.embedDiagnostics(extractionResult, actualGrammarDiagnostics)

                getFileComparisonFailExecutableIfNotEqual(
                    "Grammar diagnostics are not equal",
                    content,
                    grammarWithActualDiagnostics, file
                )?.let {
                    failExecutables.add(it)
                }
            }
            "md" -> {
                val (testDescriptor, refinedInput, diagnosticInfos) = getAndCheckTestDescriptor(content, file)

                val grammarResults = mutableListOf<GrammarPipelineResult>()
                val embeddedInfos = mutableListOf<InfoWithDescriptor<*>>()

                for (grammar in testDescriptor.grammars) {
                    val (grammarPipelineResult, grammarFailExecutables) = runGrammarPipeline(
                        grammar.value,
                        grammar.sourceInterval.offset,
                        grammarName = null,
                        file.parentFile
                    ) {
                        embeddedInfos.add(it.toInfoWithDescriptor())
                    }

                    grammarResults.add(grammarPipelineResult)
                    failExecutables.addAll(grammarFailExecutables)
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

                getFileComparisonFailExecutableIfNotEqual(
                    "Grammar diagnostics or dumps are not equal",
                    content,
                    actual,
                    file
                )?.let {
                    failExecutables.add(it)
                }
            }
            else -> fail("Valid extensions are `g4` and `md`")
        }
        assertAll(failExecutables)
    }

    private fun runGrammarPipeline(
        grammarText: CharSequence,
        grammarOffset: Int,
        grammarName: String?,
        parentFile: File,
        diagnosticReporter: ((AntlrDiagnostic) -> Unit),
    ): Pair<GrammarPipelineResult, List<() -> Unit>> {
        val failExecutables = mutableListOf<() -> Unit>()
        val result = GrammarPipeline.run(grammarText, grammarOffset, debugMode = true) {
            diagnosticReporter.invoke(it)
        }

        val subdirectory = parentFile.path.substringAfter(resourcesFile.path)
        val dumpName = grammarName ?: result.grammarName
        if (subdirectory.contains("Atn")) {
            dumpAtn(result.originalAtn!!, result.lineOffsets, parentFile, dumpName, minimized = false)?.let {
                failExecutables.add(it)
            }
            dumpAtn(result.minimizedAtn, result.lineOffsets, parentFile, dumpName, minimized = true)?.let {
                failExecutables.add(it)
            }
        } else if (subdirectory.contains("Types")) {
            (dumpTypes(result.typesInfo, result.lineOffsets, parentFile, dumpName))?.let {
                failExecutables.add(it)
            }
        }

        return result to failExecutables
    }

    private fun dumpAtn(atn: Atn, lineOffsets: List<Int>, parentFile: File, name: String?, minimized: Boolean): (() -> Unit)? {
        val actualAtnDump = AtnDumper(lineOffsets).dump(atn)
        val dumpFile = Paths.get(parentFile.path, "${name}${if (minimized) ".min" else ""}.dot").toFile()
        return if (!dumpFile.exists()) {
            dumpFile.writeText(actualAtnDump)
            getFileNotExistFailExecutable(dumpFile)
        } else {
            val expectedAtnDump = dumpFile.readText()
            getFileComparisonFailExecutableIfNotEqual(
                "ATN ${if (minimized) "minimized " else ""} dumps are not equal", expectedAtnDump, actualAtnDump, dumpFile
            )
        }
    }

    private fun dumpTypes(typesInfo: TypesInfo, lineOffsets: List<Int>, parentFile: File, name: String?): (() -> Unit)? {
        val actualDump = TypesDumper(lineOffsets).dump(typesInfo)
        val dumpFile = Paths.get(parentFile.path, "${name}.types").toFile()
        return if (!dumpFile.exists()) {
            dumpFile.writeText(actualDump)
            getFileNotExistFailExecutable(dumpFile)
        } else {
            val expectedDump = dumpFile.readText()
            getFileComparisonFailExecutableIfNotEqual(
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
            throwAssertionFailedError("Test descriptor contains errors", file, inputWithoutDescriptorErrors, inputWithDescriptorErrors)
        }

        return TestDescriptorInfo(testDescriptor, refinedInput, otherDiagnostics)
    }

    private data class TestDescriptorInfo(
        val testDescriptor: TestDescriptor,
        val refinedInput: String,
        val regularDiagnosticInfos: List<DiagnosticInfo>,
    )

    private fun getFileNotExistFailExecutable(file: File): (() -> Unit) {
        return { fail("Expected file doesn't exist. Generating: ${file.path}") }
    }

    private fun getFileComparisonFailExecutableIfNotEqual(message: String, expected: String, actual: String, file: File): (() -> Unit)? {
        return if (expected.normalizeText() != actual.normalizeText()) {
            { throwAssertionFailedError(message, file, expected, actual) }
        } else {
            null
        }
    }

    private fun throwAssertionFailedError(
        message: String,
        file: File,
        expected: String,
        actual: String
    )  {
        throw AssertionFailedError(message, FileInfo(file.path, expected.toByteArray()), actual)
    }
}