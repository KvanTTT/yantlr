package infrastructureTests

import LineColumnBorders
import infrastructure.*
import infrastructure.testDescriptors.*
import parser.getLineColumnBorders
import parser.getLineOffsets
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

object TestDescriptorTests {
    @Test
    fun twoGrammars() {
        val (descriptor, _, lineOffsets) = parseTestDescriptor("TwoGrammars.md")

        assertEquals("TwoGrammars", descriptor.name)
        assertEquals("Test descriptor with 2 grammars", descriptor.notes.single().value)
        assertEquals(2, descriptor.grammars.size)

        val firstGrammar = descriptor.grammars[0]
        assertEquals(
            """
            grammar B;
            Y : 'y';
        """.trimIndent().replace("\n", System.lineSeparator()),
            firstGrammar.value
        )
        assertEquals(
            LineColumnBorders(7, 1, 8, 9),
            firstGrammar.sourceInterval.getLineColumnBorders(lineOffsets)
        )

        val secondGrammar = descriptor.grammars[1]
        assertEquals(
            """
            grammar A;
            /*❗UnrecognizedToken❗*/`/*❗*/;
            X : 'x';
        """.trimIndent().replace("\n", System.lineSeparator()),
            secondGrammar.value
        )
        assertEquals(
            LineColumnBorders(11, 1, 13, 9),
            secondGrammar.sourceInterval.getLineColumnBorders(lineOffsets)
        )
    }

    @Test
    fun noEmptyGrammarAtTheEnd() {
        val (descriptor, _, _) = parseTestDescriptor("CodeFenceAtTheEnd.md")
        assertEquals(1, descriptor.grammars.size)
    }

    @Test
    fun emptyPropertyValueForEmptySectionAtTheEnd() {
        val (descriptor, _, lineOffsets) = parseTestDescriptor("EmptyPropertyValueForEmptySectionAtTheEnd.md")
        val inputElement = descriptor.input.single()

        assertEquals(LineColumnBorders(12, 8) , inputElement.sourceInterval.getLineColumnBorders(lineOffsets))
    }

    @Test
    fun emptyPropertyValueForEmptySectionAtTheMiddle() {
        val (descriptor, _, lineOffsets) = parseTestDescriptor("EmptyPropertyValueForEmptySectionAtTheMiddle.md")
        val inputElement = descriptor.input.single()

        assertEquals(LineColumnBorders(5, 8) , inputElement.sourceInterval.getLineColumnBorders(lineOffsets))
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
        assertIs<UnknownPropertyDiagnostic>(diagnostic)
        assertEquals("UnknownProperty", diagnostic.arg)
        assertEquals(LineColumnBorders(5, 3, 18), diagnostic.sourceInterval.getLineColumnBorders(lineOffsets))
    }

    @Test
    fun duplicatedProperty() {
        val (_, diagnostics, lineOffsets) = parseTestDescriptor("Errors/DuplicatedProperty.md")

        val diagnostic = diagnostics.single()
        assertIs<DuplicatedPropertyDiagnostic>(diagnostic)
        assertEquals("Grammars", diagnostic.arg)
        assertEquals(LineColumnBorders(12, 3, 11), diagnostic.sourceInterval.getLineColumnBorders(lineOffsets))
    }

    @Test
    fun duplicatedValue() {
        val (_, diagnostics, lineOffsets) = parseTestDescriptor("Errors/DuplicatedValue.md")

        val diagnostic = diagnostics.single()
        assertIs<DuplicatedValueDiagnostic>(diagnostic)
        assertEquals("name", diagnostic.arg)
        assertEquals(LineColumnBorders(5, 1, 14), diagnostic.sourceInterval.getLineColumnBorders(lineOffsets))
    }

    @Test
    fun missingGrammars() {
        val (_, diagnostics, lineOffsets) = parseTestDescriptor("Errors/MissingGrammars.md")

        val diagnostic = diagnostics.single()
        assertIs<MissingPropertyDiagnostic>(diagnostic)
        assertEquals("Grammars", diagnostic.arg)
        assertEquals(LineColumnBorders(4, 1, 1), diagnostic.sourceInterval.getLineColumnBorders(lineOffsets))
    }

    private fun parseTestDescriptor(name: String): Triple<TestDescriptor, List<TestDescriptorDiagnostic>, List<Int>> {
        val file = Paths.get(resourcesFile.toString(), "Infrastructure", name).toFile()
        val diagnostics = mutableListOf<TestDescriptorDiagnostic>()
        val testDescriptor =
            TestDescriptorExtractor.extract(file.readText(), file.nameWithoutExtension) { diagnostics.add(it) }
        return Triple(testDescriptor, diagnostics, file.readText().getLineOffsets())
    }
}