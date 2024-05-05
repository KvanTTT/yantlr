import atn.Atn
import atn.AtnBuilder
import atn.AtnCloner
import atn.AtnMinimizer
import parser.AntlrLexer
import parser.AntlrLexerTokenStream
import parser.AntlrParser
import semantics.RuleCollector

object GrammarPipeline {
    fun run(
        grammarText: CharSequence,
        grammarOffset: Int = 0,
        diagnosticReporter: ((AntlrDiagnostic) -> Unit)? = null
    ): GrammarPipelineResult {
        val lexer = AntlrLexer(grammarText, textOffset = grammarOffset, diagnosticReporter = diagnosticReporter)
        val tree = AntlrParser(AntlrLexerTokenStream(lexer), diagnosticReporter = diagnosticReporter).parseGrammar()

        val rules = RuleCollector(lexer, diagnosticReporter = diagnosticReporter).collect(tree)
        val atn = AtnBuilder(lexer, rules, diagnosticReporter = diagnosticReporter).build(tree)
        var minimizedAtn = AtnMinimizer().removeEpsilonTransitions(AtnCloner.clone(atn))

        return GrammarPipelineResult(tree.parserIdToken.value, atn, minimizedAtn)
    }
}

class GrammarPipelineResult(val grammarName: String?, val atn: Atn, val minimizedAtn: Atn)