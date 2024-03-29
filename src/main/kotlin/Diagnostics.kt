abstract class AntlrDiagnostic(val severity: DiagnosticSeverity, sourceInterval: SourceInterval) : Diagnostic(sourceInterval)

abstract class LexerDiagnostic(val value: String, severity: DiagnosticSeverity, sourceInterval: SourceInterval) : AntlrDiagnostic(severity, sourceInterval)

class UnrecognizedToken(value: String, sourceInterval: SourceInterval) : LexerDiagnostic(value, DiagnosticSeverity.Error, sourceInterval)

class InvalidEscaping(value: String, sourceInterval: SourceInterval) : LexerDiagnostic(value, DiagnosticSeverity.Error, sourceInterval)

abstract class ParserDiagnostic(severity: DiagnosticSeverity, sourceInterval: SourceInterval) : AntlrDiagnostic(severity, sourceInterval)

class MissingToken(sourceInterval: SourceInterval) : ParserDiagnostic(DiagnosticSeverity.Error, sourceInterval)

class ExtraToken(sourceInterval: SourceInterval) : ParserDiagnostic(DiagnosticSeverity.Error, sourceInterval)

abstract class SemanticsDiagnostics(severity: DiagnosticSeverity, sourceInterval: SourceInterval) : AntlrDiagnostic(severity, sourceInterval)

class RuleRedefinition(val ruleName: String, val previousSourceInterval: SourceInterval, sourceInterval: SourceInterval) : SemanticsDiagnostics(DiagnosticSeverity.Error, sourceInterval)

enum class DiagnosticSeverity {
    Error,
    Warning,
}