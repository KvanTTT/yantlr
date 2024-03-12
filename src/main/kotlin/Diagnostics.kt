import parser.AntlrToken

abstract class AntlrDiagnostic(val severity: DiagnosticSeverity, val sourceInterval: SourceInterval)

class UnrecognizedToken(val token: AntlrToken, sourceInterval: SourceInterval) : AntlrDiagnostic(DiagnosticSeverity.Error, sourceInterval)

abstract class ParserDiagnostic(severity: DiagnosticSeverity, sourceInterval: SourceInterval) : AntlrDiagnostic(severity, sourceInterval)

class MissingToken(sourceInterval: SourceInterval) : ParserDiagnostic(DiagnosticSeverity.Error, sourceInterval)

class ExtraToken(sourceInterval: SourceInterval) : ParserDiagnostic(DiagnosticSeverity.Error, sourceInterval)

enum class DiagnosticSeverity {
    Error,
    Warning,
}