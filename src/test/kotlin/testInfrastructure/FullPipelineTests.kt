package testInfrastructure

import com.intellij.rt.execution.junit.FileComparisonFailure
import helpers.FullPipelineRunner
import helpers.resourcesFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import java.nio.file.Paths
import kotlin.test.Test

object FullPipelineTests {
    @Test
    fun grammarExample() {
        FullPipelineRunner.run(Paths.get(resourcesFile.toString(), "Infrastructure", "GrammarExample.g4").toFile())
    }

    @Test
    fun fullPipelineExample() {
        FullPipelineRunner.run(Paths.get(resourcesFile.toString(), "Infrastructure", "FullPipelineExample.md").toFile())
    }

    @Test
    fun dontRunPipelineIfTestDescriptorErrors() {
        val file = Paths.get(resourcesFile.toString(), "Infrastructure", "TestDescriptorWithErrors.md").toFile()

        val exception = assertThrows<FileComparisonFailure> { FullPipelineRunner.run(file) }

        assertEquals(DescriptorEmbeddedDiagnosticsTests.reifiedInput, exception.expected)
        assertEquals(file.readText(), exception.actual)
        assertEquals(file.path, exception.filePath)
    }
}