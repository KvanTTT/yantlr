import parser.AntlrTreeNode
import parser.RuleNode
import semantics.Rule

abstract class AntlrDiagnostic(val severity: DiagnosticSeverity, sourceInterval: SourceInterval) : Diagnostic(sourceInterval)

abstract class LexerDiagnostic(val value: String, severity: DiagnosticSeverity, sourceInterval: SourceInterval) : AntlrDiagnostic(severity, sourceInterval)

class UnrecognizedToken(value: String, sourceInterval: SourceInterval) : LexerDiagnostic(value, DiagnosticSeverity.Error, sourceInterval)

class InvalidEscaping(value: String, sourceInterval: SourceInterval) : LexerDiagnostic(value, DiagnosticSeverity.Error, sourceInterval)

abstract class ParserDiagnostic(severity: DiagnosticSeverity, sourceInterval: SourceInterval) : AntlrDiagnostic(severity, sourceInterval)

class MissingToken(sourceInterval: SourceInterval) : ParserDiagnostic(DiagnosticSeverity.Error, sourceInterval)

class ExtraToken(sourceInterval: SourceInterval) : ParserDiagnostic(DiagnosticSeverity.Error, sourceInterval)

abstract class SemanticsDiagnostics(severity: DiagnosticSeverity, sourceInterval: SourceInterval) : AntlrDiagnostic(severity, sourceInterval)

class RuleRedefinition(val previousRule: Rule, val ruleNode: RuleNode) : SemanticsDiagnostics(DiagnosticSeverity.Error, ruleNode.idToken.getInterval())

class EmptyToken(val rule: Rule) : SemanticsDiagnostics(DiagnosticSeverity.Warning, rule.ruleNode.idToken.getInterval())

class ReversedInterval(treeNode: AntlrTreeNode) : SemanticsDiagnostics(DiagnosticSeverity.Error, treeNode.getInterval())

class EmptyStringOrSet(treeNode: AntlrTreeNode) : SemanticsDiagnostics(DiagnosticSeverity.Error, treeNode.getInterval())

enum class DiagnosticSeverity {
    Error,
    Warning,
}