import atn.*
import parser.AntlrLexer
import parser.AntlrLexerTokenStream
import parser.AntlrParser
import declarations.DeclarationCollector
import declarations.DeclarationsInfo
import types.TypesBuilder
import types.TypesInfo

object GrammarPipeline {
    fun run(
        grammarText: CharSequence,
        grammarOffset: Int = 0,
        debugMode: Boolean = false,
        diagnosticReporter: ((AntlrDiagnostic) -> Unit)? = null,
    ): GrammarPipelineResult {
        val lexer = AntlrLexer(grammarText, textOffset = grammarOffset, diagnosticReporter = diagnosticReporter)
        val tree = AntlrParser(AntlrLexerTokenStream(lexer), diagnosticReporter = diagnosticReporter).parseGrammar()

        val declarationsInfo = DeclarationCollector(lexer, diagnosticReporter = diagnosticReporter).collect(tree)

        val typesInfo = TypesBuilder(declarationsInfo, diagnosticReporter = diagnosticReporter).build()

        val atn = AtnBuilder(diagnosticReporter = diagnosticReporter).build(declarationsInfo)
        val minimizedAtn: Atn
        val originalAtn: Atn?
        if (debugMode) {
            originalAtn = atn
            minimizedAtn = AtnCloner.clone(atn)
        } else {
            originalAtn = null
            minimizedAtn = atn
        }
        AtnEpsilonRemover(diagnosticReporter).run(minimizedAtn)
        AtnNegationRemover(diagnosticReporter).run(minimizedAtn)
        AtnDisambiguator(diagnosticReporter).run(minimizedAtn)
        AtnVerifier(checkNoEpsilons = true).verify(minimizedAtn)

        return GrammarPipelineResult(tree.parserIdToken.value, lexer.lineOffsets, declarationsInfo, typesInfo, originalAtn, minimizedAtn)
    }
}

class GrammarPipelineResult(
    val grammarName: String?,
    val lineOffsets: List<Int>,
    val declarationsInfo: DeclarationsInfo,
    val typesInfo: TypesInfo,
    val originalAtn: Atn?, // Null if debugMode is false
    val minimizedAtn: Atn,
)