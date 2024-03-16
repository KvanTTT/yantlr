import parser.AntlrLexer
import parser.AntlrLexerTokenStream
import parser.AntlrParser
import semantics.RuleCollector

object GrammarPipeline {
    fun process(grammarText: String, diagnosticReporter: ((AntlrDiagnostic) -> Unit)? = null) {
        val lexer = AntlrLexer(grammarText) { diagnosticReporter?.invoke(it) }
        val tree = AntlrParser(AntlrLexerTokenStream(lexer)) { diagnosticReporter?.invoke(it) }.parseGrammar()

        RuleCollector(lexer) { diagnosticReporter?.invoke(it) }.collect(tree)
    }
}