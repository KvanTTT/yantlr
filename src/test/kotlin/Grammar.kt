import com.intellij.rt.execution.junit.FileComparisonFailure
import helpers.AntlrDiagnosticsHandler
import helpers.resourcesFile
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.File

object Grammar {
    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    fun diagnostics(): Iterator<DynamicNode> {
        return sequence<DynamicNode> {
            for (grammarFile in getGrammarFiles()) {
                yield(dynamicTest(grammarFile.nameWithoutExtension, grammarFile.toURI()) {
                    val input = grammarFile.readText()
                    val extractionResult = AntlrDiagnosticsHandler.extract(input)

                    val actualDiagnostics = buildList {
                        GrammarPipeline.process(extractionResult.refinedInput) { add(it) }
                    }

                    val inputWithActualDiagnostics = AntlrDiagnosticsHandler.embed(extractionResult, actualDiagnostics)

                    if (input != inputWithActualDiagnostics) {
                        throw FileComparisonFailure(
                            "Diagnostics are not equal.",
                            input,
                            inputWithActualDiagnostics,
                            grammarFile.absolutePath
                        )
                    }
                })
            }
        }.iterator()
    }

    private fun getGrammarFiles(): Sequence<File> {
        return resourcesFile.walk().filter { it.isFile && it.extension == "g4" }
    }
}