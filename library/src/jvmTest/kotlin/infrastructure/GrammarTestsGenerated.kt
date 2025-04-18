package infrastructure

import java.nio.file.Paths

abstract class GrammarTestsGenerated : GeneratedTests() {
    override fun runTest(relativeFilePath: String) {
        FullPipelineRunner.run(Paths.get(projectDir, relativeFilePath).toFile())
    }
}