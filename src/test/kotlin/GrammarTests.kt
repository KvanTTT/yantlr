import infrastructure.TestFileType
import infrastructure.createTests
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Paths

object GrammarTests {
    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    fun diagnostics(): Iterator<DynamicNode> = createTests(TestFileType.Antlr,  Paths.get("Grammar", "Diagnostics").toString())

    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    fun atnNoEpsilon(): Iterator<DynamicNode> = createTests(TestFileType.Antlr, Paths.get("Grammar", "Atn", "NoEpsilon").toString())

    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    fun atnDisambiguated(): Iterator<DynamicNode> = createTests(TestFileType.Antlr, Paths.get("Grammar", "Atn", "Disambiguated").toString())
}