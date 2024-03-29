package helpers

import parser.AntlrLexer
import parser.AntlrLexerTokenStream
import parser.AntlrParser
import java.nio.file.Paths

fun <T> check(expectedTreeFragment: T, grammarFragment: String, parseFunc: (AntlrParser) -> T) {
    val lexer = AntlrLexer(grammarFragment)
    val tokenStream = AntlrLexerTokenStream(lexer)
    val parser = AntlrParser(tokenStream)
    val actualNode = parseFunc(parser)

    AntlrTreeComparer(lexer).compare(expectedTreeFragment, actualNode)
}

val resourcesFile = Paths.get(System.getProperty("user.dir"), "src", "test", "resources").toFile()