package helpers

import parser.AntlrLexer
import parser.AntlrLexerTokenStream
import parser.AntlrParser

fun <T> check(expectedTreeFragment: T, grammarFragment: String, parseFunc: (AntlrParser) -> T) {
    val lexer = AntlrLexer(grammarFragment)
    val tokenStream = AntlrLexerTokenStream(lexer)
    val parser = AntlrParser(tokenStream)
    val actualNode = parseFunc(parser)

    AntlrTreeComparer(lexer).compare(expectedTreeFragment, actualNode)
}