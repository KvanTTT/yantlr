package infrastructure

import AntlrDiagnostic
import InfoWithSourceInterval
import infrastructure.testDescriptors.TestDescriptorDiagnostic
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import parser.AntlrLexer
import parser.AntlrLexerTokenStream
import parser.AntlrParser
import java.io.File
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.streams.asStream

fun <T> check(expectedTreeFragment: T, grammarFragment: String, parseFunc: (AntlrParser) -> T) {
    val lexer = AntlrLexer(grammarFragment)
    val tokenStream = AntlrLexerTokenStream(lexer)
    val parser = AntlrParser(tokenStream)
    val actualNode = parseFunc(parser)

    AntlrTreeComparer(lexer).compare(expectedTreeFragment, actualNode)
}

val resourcesFile = Paths.get(System.getProperty("user.dir"), "src", "test", "resources").toFile()

enum class TestFileType {
    Antlr,
    Md,
}

fun createTests(fileType: TestFileType): Iterator<DynamicNode> {
    val subdir = if (fileType == TestFileType.Antlr) "Grammar" else "FullPipeline"
    return Paths.get(resourcesFile.toString(), subdir).toFile().getChildrenTests(fileType).iterator()
}

private fun File.getChildrenTests(fileType: TestFileType): Sequence<DynamicNode> {
    return walk().maxDepth(1).filter {
        it != this && (it.isDirectory || it.extension == if (fileType == TestFileType.Antlr) "g4" else "md")
    }.map { it.createTest(fileType) }
}

private fun File.createTest(fileType: TestFileType): DynamicNode {
    return if (isDirectory) {
        DynamicContainer.dynamicContainer(name, toURI(), getChildrenTests(fileType).asStream())
    } else {
        DynamicTest.dynamicTest(nameWithoutExtension, toURI()) { FullPipelineRunner.run(this) }
    }
}

fun InfoWithSourceInterval.toInfoWithDescriptor(): InfoWithDescriptor<InfoWithSourceInterval> {
    val descriptor = when (this) {
        is AntlrDiagnostic -> AntlrDiagnosticInfoDescriptor
        is TestDescriptorDiagnostic -> TestDescriptorDiagnosticInfoDescriptor
        is DiagnosticInfo -> this.descriptor
        else -> error("Unknown diagnostic type")
    }
    @Suppress("UNCHECKED_CAST")
    return InfoWithDescriptor(this, descriptor as EmbeddedInfoDescriptor<InfoWithSourceInterval>)
}