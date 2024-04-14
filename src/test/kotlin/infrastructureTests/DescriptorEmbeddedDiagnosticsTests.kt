package infrastructureTests

import infrastructure.InfoEmbedder
import infrastructure.TestDescriptorDiagnosticsExtractor
import infrastructure.resourcesFile
import infrastructure.testDescriptors.TestDescriptorExtractor
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

object DescriptorEmbeddedDiagnosticsTests {
    val refinedInput = """
        # Name

        Custom name 0

        Custom name 1

        # Input

        ```
        INPUT 0
        ```

        # Input

        ```
        INPUT 1
        ```

        # UnknownProperty
    """.trimIndent().replace("\n", System.lineSeparator())

    @Test
    fun example() {
        val inputFile = Paths.get(resourcesFile.toString(), "Infrastructure", "TestDescriptorWithErrors.md").toFile()
        val input = inputFile.readText()

        val extractionResult = TestDescriptorDiagnosticsExtractor.extract(inputFile.readText())

        assertEquals(refinedInput, extractionResult.refinedInput)

        val actualDiagnostics = buildList {
            TestDescriptorExtractor.extract(extractionResult.refinedInput, inputFile.nameWithoutExtension) { add(it) }
        }

        val inputWithEmbeddedActualDiagnostics = InfoEmbedder.embedDiagnostics(extractionResult, actualDiagnostics)

        assertEquals(input, inputWithEmbeddedActualDiagnostics)
    }
}