import parser.AntlrToken

abstract class AntlrDiagnostic(
    val stage: DiagnosticStage,
    val severity: DiagnosticSeverity,
    val start: Int,
    val length: Int,
)

class UnrecognizedToken(val token: AntlrToken, start: Int, length: Int) : AntlrDiagnostic(DiagnosticStage.Lexer, DiagnosticSeverity.Error, start, length)

class MissingToken(val token: AntlrToken, start: Int, length: Int) : AntlrDiagnostic(DiagnosticStage.Parser, DiagnosticSeverity.Error, start, length)

class ExtraToken(val token: AntlrToken, start: Int, length: Int) : AntlrDiagnostic(DiagnosticStage.Parser, DiagnosticSeverity.Error, start, length)

enum class DiagnosticStage {
    Lexer,
    Parser,
    Semantics,
}

enum class DiagnosticSeverity {
    Error,
    Warning,
}