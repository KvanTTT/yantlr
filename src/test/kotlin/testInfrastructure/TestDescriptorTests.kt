package testInfrastructure

import LineColumn
import helpers.testDescriptors.TestDescriptorExtractor
import org.junit.jupiter.api.Test
import parser.getLineColumn
import parser.getLineIndexes
import java.nio.file.Paths
import kotlin.test.assertEquals

class TestDescriptorTests {
    val resourcesPath = Paths.get(System.getProperty("user.dir"), "src", "test", "resources").toFile()

    @Test
    fun testDescriptorWith2Grammars() {
        val file = getFile("TestDescriptorWith2Grammars.md")
        val lineIndexes = file.readText().getLineIndexes()
        val descriptor = TestDescriptorExtractor.extract(file)
        assertEquals("TestDescriptorWith2Grammars", descriptor.name)
        assertEquals("Test descriptor with 2 grammars", descriptor.notes?.value)
        assertEquals(2, descriptor.grammars.size)

        val firstGrammar = descriptor.grammars[0]
        assertEquals("""
            grammar B;
            Y : 'y';
        """.trimIndent().replace("\n", System.lineSeparator()),
            firstGrammar.value
        )
        assertEquals(LineColumn(7, 1), firstGrammar.offset.getLineColumn(lineIndexes))

        val secondGrammar = descriptor.grammars[1]
        assertEquals("""
            grammar A;
            /*❗UnrecognizedToken❗*/`/*❗*/;
            X : 'x';
        """.trimIndent().replace("\n", System.lineSeparator()),
            secondGrammar.value
        )
        assertEquals(LineColumn(11, 1), secondGrammar.offset.getLineColumn(lineIndexes))

        assertEquals("", descriptor.input?.value)
    }

    private fun getFile(name: String) = Paths.get(resourcesPath.absolutePath, "TestDescriptors", name).toFile()
}