import parser.AntlrLexer
import parser.AntlrLexerTokenStream
import parser.AntlrParser
import semantics.RuleCollector

object GrammarPipeline {
    fun run(grammarText: CharSequence, grammarOffset: Int = 0, diagnosticReporter: ((AntlrDiagnostic) -> Unit)? = null): GrammarPipelineResult {
        val lexer = AntlrLexer(grammarText, textOffset = grammarOffset, diagnosticReporter = diagnosticReporter)
        val tree = AntlrParser(AntlrLexerTokenStream(lexer), diagnosticReporter = diagnosticReporter).parseGrammar()

        RuleCollector(lexer, diagnosticReporter = diagnosticReporter).collect(tree)
        return GrammarPipelineResult()
    }
}

class GrammarPipelineResult