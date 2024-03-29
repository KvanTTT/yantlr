package testInfrastructure

import LineColumn
import helpers.testDescriptors.TestDescriptor
import helpers.testDescriptors.TestDescriptorDiagnostic
import helpers.testDescriptors.TestDescriptorDiagnosticType
import helpers.testDescriptors.TestDescriptorExtractor
import org.junit.jupiter.api.Test
import parser.getLineColumn
import parser.getLineIndexes
import java.nio.file.Paths
import kotlin.test.assertEquals

object TestDescriptorTests {
    private val resourcesPath = Paths.get(System.getProperty("user.dir"), "src", "test", "resources").toFile().absolutePath

    @Test
    fun twoGrammars() {
        val (descriptor, _, lineOffsets) = parseTestDescriptor("TwoGrammars.md")

        assertEquals("TwoGrammars", descriptor.name)
        assertEquals("Test descriptor with 2 grammars", descriptor.notes.single().value)
        assertEquals(2, descriptor.grammars.size)

        val firstGrammar = descriptor.grammars[0]
        assertEquals("""
            grammar B;
            Y : 'y';
        """.trimIndent().replace("\n", System.lineSeparator()),
            firstGrammar.value
        )
        assertEquals(LineColumn(7, 1), firstGrammar.offset.getLineColumn(lineOffsets))

        val secondGrammar = descriptor.grammars[1]
        assertEquals("""
            grammar A;
            /*❗UnrecognizedToken❗*/`/*❗*/;
            X : 'x';
        """.trimIndent().replace("\n", System.lineSeparator()),
            secondGrammar.value
        )
        assertEquals(LineColumn(11, 1), secondGrammar.offset.getLineColumn(lineOffsets))

        assertEquals("", descriptor.input.single().value)
    }

    @Test
    fun customName() {
        val (descriptor, _, _) = parseTestDescriptor("CustomName.md")
        assertEquals("My name", descriptor.name)
    }

    @Test
    fun headlessNotes() {
        val (descriptor, _, _) = parseTestDescriptor("HeadlessNotes.md")
        assertEquals("Notes without a header", descriptor.notes.single().value)
    }

    @Test
    fun unknownProperty() {
        val (_, diagnostics, lineOffsets) = parseTestDescriptor("Errors/UnknownProperty.md")

        val diagnostic = diagnostics.single()
        assertEquals(TestDescriptorDiagnosticType.UnknownProperty, diagnostic.type)
        assertEquals("UnknownProperty", diagnostic.arg)
        assertEquals(LineColumn(5, 1), diagnostic.sourceInterval.offset.getLineColumn(lineOffsets))
    }

    @Test
    fun duplicatedProperty() {
        val (_, diagnostics, lineOffsets) = parseTestDescriptor("Errors/DuplicatedProperty.md")

        val diagnostic = diagnostics.single()
        assertEquals(TestDescriptorDiagnosticType.DuplicatedProperty, diagnostic.type)
        assertEquals("Grammars", diagnostic.arg)
        assertEquals(LineColumn(12, 1), diagnostic.sourceInterval.offset.getLineColumn(lineOffsets))
    }

    @Test
    fun duplicatedValue() {
        val (_, diagnostics, lineOffsets) = parseTestDescriptor("Errors/DuplicatedValue.md")

        val diagnostic = diagnostics.single()
        assertEquals(TestDescriptorDiagnosticType.DuplicatedValue, diagnostic.type)
        assertEquals("name", diagnostic.arg)
        assertEquals(LineColumn(5, 1), diagnostic.sourceInterval.offset.getLineColumn(lineOffsets))
    }

    @Test
    fun missingGrammars() {
        val (_, diagnostics, lineOffsets) = parseTestDescriptor("Errors/MissingGrammars.md")

        val diagnostic = diagnostics.single()
        assertEquals(TestDescriptorDiagnosticType.MissingProperty, diagnostic.type)
        assertEquals("Grammars", diagnostic.arg)
        assertEquals(LineColumn(4, 1), diagnostic.sourceInterval.offset.getLineColumn(lineOffsets))
    }

    private fun parseTestDescriptor(name: String): Triple<TestDescriptor, List<TestDescriptorDiagnostic>, List<Int>> {
        val file = Paths.get(resourcesPath, "TestDescriptors", name).toFile()
        val diagnostics = mutableListOf<TestDescriptorDiagnostic>()
        val testDescriptor = TestDescriptorExtractor.extract(file) { diagnostics.add(it) }
        return Triple(testDescriptor, diagnostics, file.readText().getLineIndexes())
    }
}