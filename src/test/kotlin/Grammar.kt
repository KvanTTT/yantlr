import com.intellij.rt.execution.junit.FileComparisonFailure
import helpers.CustomDiagnosticsHandler
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.File
import java.nio.file.Paths

object Grammar {
    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    fun diagnostics(): Iterator<DynamicNode> {
        return sequence<DynamicNode> {
            for (grammarFile in getGrammarFiles()) {
                yield(dynamicTest(grammarFile.nameWithoutExtension, grammarFile.toURI()) {
                    val input = grammarFile.readText()
                    val extractionResult = CustomDiagnosticsHandler.extract(input)

                    val actualDiagnostics = buildList {
                        GrammarPipeline.process(extractionResult.refinedInput) { add(it) }
                    }

                    val inputWithActualDiagnostics = CustomDiagnosticsHandler.embed(extractionResult, actualDiagnostics)

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
        val resourcesPath = Paths.get(System.getProperty("user.dir"), "src", "test", "resources")
        return resourcesPath.toFile().walk().filter { it.isFile && it.extension == "g4" }
    }
}