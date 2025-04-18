import java.io.File

class TestGenerator(val projectDir: String) {
    companion object {
        private const val LIBRARY_RELATIVE_PATH = "library/src"
        private const val RESOURCE_RELATIVE_PATH = "$LIBRARY_RELATIVE_PATH/jvmTest/resources"
        private const val GRAMMARS_RELATIVE_PATH = "$RESOURCE_RELATIVE_PATH/Grammar"
        private const val GENERATED_PATH = "$LIBRARY_RELATIVE_PATH/jvmTest/kotlin/generated"
    }

    private val indentCache = mutableListOf<String>()

    fun generateGrammarTests() {
        val generatedTests = buildString {
            appendLine("package generated")
            appendLine()
            append("""
            import com.intellij.testFramework.TestDataPath
            import infrastructure.GrammarTestsGenerated
            import org.jetbrains.kotlin.test.TestMetadata
            import org.junit.jupiter.api.Nested
            import kotlin.test.Test
        """.trimIndent())
            appendLine()

            appendClass(GRAMMARS_RELATIVE_PATH, 0)
        }

        val generatedFile = createFile("$GENERATED_PATH/Grammar.kt")
        generatedFile.parentFile.mkdirs()
        generatedFile.writeText(generatedTests)
    }

    private fun StringBuilder.appendClass(directoryRelativePath: String, level: Int) {
        val file = createFile(directoryRelativePath)

        if (file.name == ".antlr") return // Ignore generated helper files by other tools

        val isInner = level > 0
        if (isInner) {
            appendIndentedLine("@Nested", level)
        }
        appendIndentedLine("@TestDataPath(\"\\\$PROJECT_ROOT\")", level)
        appendIndentedLine("@TestMetadata(\"$directoryRelativePath\")", level)

        appendIndent(level)
        if (isInner) {
            append("inner ")
        }
        append("class ")
        append(file.name)
        if (!isInner) {
            append(" : GrammarTestsGenerated()")
        }
        appendLine(" {")

        for (childFile in file.walkTopDown().maxDepth(1)) {
            if (childFile == file) continue

            val newRelativePath = directoryRelativePath + "/" + childFile.name
            if (childFile.isDirectory) {
                appendClass(newRelativePath, level + 1)
            } else if (childFile.extension == "g4") {
                appendTest(newRelativePath, level + 1)
            }
        }

        appendIndentedLine("}", level)

        appendLine()
    }

    private fun StringBuilder.appendTest(fileRelativePath: String, level: Int) {
        val file = createFile(fileRelativePath)
        appendIndentedLine("@Test", level)
        appendIndentedLine("@TestMetadata(\"${file.name}\")", level)

        appendIndent(level)
        append("fun test")
        append(file.nameWithoutExtension.replaceFirstChar { it.uppercase() })
        append("() = runTest(\"")
        append(fileRelativePath)
        appendLine("\")")

        appendLine()
    }

    private fun StringBuilder.appendIndentedLine(line: String, level: Int) {
        appendIndent(level)
        appendLine(line)
    }

    private fun StringBuilder.appendIndent(level: Int) {
        val cacheSize = indentCache.size
        (cacheSize..level).forEach { i ->
            indentCache.add("    ".repeat(i))
        }

        append(indentCache[level])
    }

    private fun createFile(relativePath: String): File = File(projectDir.takeIf { it.isNotEmpty()} ?: ".", relativePath)
}