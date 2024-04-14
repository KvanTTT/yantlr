package infrastructureTests

import com.intellij.rt.execution.junit.FileComparisonFailure
import infrastructure.FullPipelineRunner
import infrastructure.resourcesFile
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
    fun dontRunPipelineIfTestDescriptorContainsErrors() {
        val file = Paths.get(resourcesFile.toString(), "Infrastructure", "TestDescriptorWithErrors.md").toFile()

        val exception = assertThrows<FileComparisonFailure> { FullPipelineRunner.run(file) }

        assertEquals(DescriptorEmbeddedDiagnosticsTests.refinedInput, exception.expected)
        assertEquals(file.readText(), exception.actual)
        assertEquals(file.path, exception.filePath)
    }

    @Test
    fun descriptorAndAntlrDiagnosticsInTheSameFile() {
        val file = Paths.get(resourcesFile.toString(), "Infrastructure", "TestDescriptorWithAllErrors.md").toFile()

        val exception = assertThrows<FileComparisonFailure> { FullPipelineRunner.run(file) }

        val expected = """
# Notes

Test runner should report `UnknownProperty` but preserved ANTLR errors

# Grammars

```antlrv4
grammar grammarExample
/*❗UnrecognizedToken*/`/*❗*/
/*❗MissingToken*//*❗*//*❗ExtraToken*/+/*❗*/
```

# UnknownProperty
""".trimIndent().replace("\n", System.lineSeparator())

        assertEquals(expected, exception.expected)
    }
}