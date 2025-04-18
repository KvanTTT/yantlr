package infrastructure

import java.io.File

abstract class GeneratedTests {
    companion object {
        val projectDir: String = File(System.getProperty("user.dir")).parent
    }

    abstract fun runTest(relativeFilePath: String)
}