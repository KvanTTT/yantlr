package testInfrastructure

import helpers.TestDescriptorDiagnosticsHandler
import helpers.resourcesFile
import helpers.testDescriptors.TestDescriptorExtractor
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

class DescriptorEmbeddedDiagnosticsTests {
    @Test
    fun example() {
        val inputFile = Paths.get(resourcesFile.toString(), "TestDescriptors", "EmbeddedDiagnostics", "Example.md").toFile()
        val input = inputFile.readText()

        val extractionResult = TestDescriptorDiagnosticsHandler.extract(inputFile.readText())

        assertEquals("""
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
        """.trimIndent().replace("\n", System.lineSeparator()),

            extractionResult.refinedInput
        )

        val actualDiagnostics = buildList {
            TestDescriptorExtractor.extract(extractionResult.refinedInput, inputFile.nameWithoutExtension) { add(it) }
        }

        val inputWithEmbeddedActualDiagnostics = TestDescriptorDiagnosticsHandler.embed(extractionResult, actualDiagnostics)

        assertEquals(input, inputWithEmbeddedActualDiagnostics)
    }
}