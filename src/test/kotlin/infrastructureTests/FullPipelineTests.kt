package infrastructureTests

import infrastructure.FullPipelineRunner
import infrastructure.resourcesFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.opentest4j.AssertionFailedError
import org.opentest4j.FileInfo
import java.nio.charset.Charset
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

        val exception = assertThrows<AssertionFailedError> { FullPipelineRunner.run(file) }

        val fileInfo = exception.expected.value as FileInfo
        assertEquals(DescriptorEmbeddedDiagnosticsTests.refinedInput, fileInfo.getContentsAsString(Charset.defaultCharset()))
        assertEquals(file.readText(), exception.actual.value)
        assertEquals(file.path, fileInfo.path)
    }

    @Test
    fun descriptorAndAntlrDiagnosticsInTheSameFile() {
        val file = Paths.get(resourcesFile.toString(), "Infrastructure", "TestDescriptorWithAllErrors.md").toFile()

        val exception = assertThrows<AssertionFailedError> { FullPipelineRunner.run(file) }

        val expected = """
# Notes

Test runner should report `UnknownProperty` but preserve ANTLR errors

# Grammars

```antlrv4
grammar grammarExample
/*❗UnrecognizedToken*/`/*❗*/
/*❗MissingToken*//*❗*//*❗ExtraToken*/+/*❗*/
```

# UnknownProperty
""".trimIndent().replace("\n", System.lineSeparator())

        assertEquals(expected, (exception.expected.value as FileInfo).getContentsAsString(Charset.defaultCharset()))
    }
}