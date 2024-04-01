import infrastructure.TestFileType
import infrastructure.createTests
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

object GrammarTests {
    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    fun run(): Iterator<DynamicNode> = createTests(TestFileType.Antlr)
}