import com.intellij.rt.execution.junit.FileComparisonFailure
import helpers.CustomDiagnosticsHandler
import helpers.TestDescriptorExtractor
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.File
import java.nio.file.Paths

object Tests {
    val resourcesPath = Paths.get(System.getProperty("user.dir"), "src", "test", "resources").toFile()

    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    fun diagnostics(): Iterator<DynamicNode> = generate(grammar = true)

    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    fun full(): Iterator<DynamicNode> = generate(grammar = false)

    private fun generate(grammar: Boolean): Iterator<DynamicNode> {
        return sequence<DynamicNode> {
            for (file in getTestFiles(grammar)) {
                yield(dynamicTest(file.nameWithoutExtension, file.toURI()) {
                    val input = file.readText()

                    if (grammar) {
                        val extractionResult = CustomDiagnosticsHandler.extract(input)

                        val actualDiagnostics = buildList {
                            GrammarPipeline.process(extractionResult.refinedInput) { add(it) }
                        }

                        val inputWithActualDiagnostics =
                            CustomDiagnosticsHandler.embed(extractionResult, actualDiagnostics)

                        if (input != inputWithActualDiagnostics) {
                            throw FileComparisonFailure(
                                "Diagnostics are not equal.",
                                input,
                                inputWithActualDiagnostics,
                                file.absolutePath
                            )
                        }
                    } else {
                        val testDescriptor = TestDescriptorExtractor().extract(file.nameWithoutExtension, input)
                        Unit
                    }
                })
            }
        }.iterator()
    }

    private fun getTestFiles(grammar: Boolean): Sequence<File> {
        return resourcesPath.walk().filter { it.isFile && it.extension == if (grammar) "g4" else "md" }
    }
}