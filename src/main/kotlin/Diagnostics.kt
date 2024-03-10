import parser.AntlrToken

abstract class AntlrDiagnostic(
    val severity: DiagnosticSeverity,
    val offset: Int,
    val length: Int,
)

class UnrecognizedToken(val token: AntlrToken, start: Int, length: Int) : AntlrDiagnostic(DiagnosticSeverity.Error, start, length)

abstract class ParserDiagnostic(severity: DiagnosticSeverity, start: Int, length: Int) : AntlrDiagnostic(severity, start, length)

class MissingToken(val token: AntlrToken, start: Int, length: Int) : ParserDiagnostic(DiagnosticSeverity.Error, start, length)

class ExtraToken(val token: AntlrToken, start: Int, length: Int) : ParserDiagnostic(DiagnosticSeverity.Error, start, length)

enum class DiagnosticSeverity {
    Error,
    Warning,
}