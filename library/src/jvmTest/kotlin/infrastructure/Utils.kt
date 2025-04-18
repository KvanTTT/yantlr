package infrastructure

import AntlrDiagnostic
import InfoWithSourceInterval
import infrastructure.testDescriptors.TestDescriptorDiagnostic
import parser.AntlrLexer
import parser.AntlrLexerTokenStream
import parser.AntlrParser
import java.io.File
import java.nio.file.Paths

fun <T> check(expectedTreeFragment: T, grammarFragment: String, parseFunc: (AntlrParser) -> T) {
    val lexer = AntlrLexer(grammarFragment)
    val tokenStream = AntlrLexerTokenStream(lexer)
    val parser = AntlrParser(tokenStream)
    val actualNode = parseFunc(parser)

    AntlrTreeComparer(lexer).compare(expectedTreeFragment, actualNode)
}

val resourcesFile: File = Paths.get(System.getProperty("user.dir"), "src", "jvmTest", "resources").toFile()

fun InfoWithSourceInterval.toInfoWithDescriptor(): InfoWithDescriptor<InfoWithSourceInterval> {
    val descriptor = when (this) {
        is AntlrDiagnostic -> AntlrDiagnosticInfoDescriptor
        is TestDescriptorDiagnostic -> TestDescriptorDiagnosticInfoDescriptor
        is DiagnosticInfo -> this.descriptor
        is DumpInfo -> DumpInfoDescriptor
        else -> error("Unknown diagnostic type")
    }
    @Suppress("UNCHECKED_CAST")
    return InfoWithDescriptor(this, descriptor as EmbeddedInfoDescriptor<InfoWithSourceInterval>)
}

fun String.normalizeText(): String {
    return replace("\r\n", "\n")
}