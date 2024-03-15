import helpers.CustomDiagnosticsHandler
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import parser.AntlrLexer
import parser.AntlrLexerTokenStream
import parser.AntlrParser
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertEquals

object Grammar {
    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    fun diagnostics(): Iterator<DynamicNode> {
        return sequence<DynamicNode> {
            for (grammarFile in getGrammarFiles()) {
                yield(dynamicTest(grammarFile.nameWithoutExtension, grammarFile.toURI()) {
                    val input = grammarFile.readText()
                    val refinedInput = CustomDiagnosticsHandler.extract(input).refinedInput

                    val actualDiagnostics = buildList {
                        val lexer = AntlrLexer(refinedInput) { add(it) }
                        AntlrParser(AntlrLexerTokenStream(lexer)) { add(it) }.parseGrammar()
                    }

                    val inputWithDiagnostics = CustomDiagnosticsHandler.embed(refinedInput, actualDiagnostics)

                    assertEquals(input, inputWithDiagnostics)
                })
            }
        }.iterator()
    }

    private fun getGrammarFiles(): Sequence<File> {
        var path = this::class.java.protectionDomain.codeSource.location.path
        if (isWindows()) {
            path = path.replaceFirst("/", "")
        }
        val resourcesPath = Paths.get(path, "..", "..", "..", "..", "src", "test", "resources").normalize().toAbsolutePath()

        return resourcesPath.toFile().walk().filter { it.isFile && it.extension == "g4" }
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").contains("Windows")
    }
}