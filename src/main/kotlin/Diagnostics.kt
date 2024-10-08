import parser.AntlrNode
import parser.AntlrTreeNode
import parser.RuleNode
import declarations.Rule

abstract class AntlrDiagnostic(val severity: DiagnosticSeverity, sourceInterval: SourceInterval) : Diagnostic(sourceInterval)

abstract class LexerDiagnostic(val value: String, severity: DiagnosticSeverity, sourceInterval: SourceInterval) : AntlrDiagnostic(severity, sourceInterval)

class UnrecognizedToken(value: String, sourceInterval: SourceInterval) : LexerDiagnostic(value, DiagnosticSeverity.Error, sourceInterval)

class InvalidEscaping(value: String, sourceInterval: SourceInterval) : LexerDiagnostic(value, DiagnosticSeverity.Error, sourceInterval)

abstract class ParserDiagnostic(severity: DiagnosticSeverity, sourceInterval: SourceInterval) : AntlrDiagnostic(severity, sourceInterval)

class MissingToken(sourceInterval: SourceInterval) : ParserDiagnostic(DiagnosticSeverity.Error, sourceInterval)

class ExtraToken(sourceInterval: SourceInterval) : ParserDiagnostic(DiagnosticSeverity.Error, sourceInterval)

abstract class SemanticsDiagnostic(severity: DiagnosticSeverity, sourceInterval: SourceInterval) : AntlrDiagnostic(severity, sourceInterval)

class RuleRedefinition(val previousRule: Rule, val ruleNode: RuleNode) : SemanticsDiagnostic(DiagnosticSeverity.Error, ruleNode.idToken.getInterval())

class EmptyToken(val rule: Rule) : SemanticsDiagnostic(DiagnosticSeverity.Warning, rule.treeNode.idToken.getInterval())

class ReversedInterval(treeNode: AntlrTreeNode) : SemanticsDiagnostic(DiagnosticSeverity.Error, treeNode.getInterval())

class EmptyStringOrSet(treeNode: AntlrTreeNode) : SemanticsDiagnostic(DiagnosticSeverity.Error, treeNode.getInterval())

class MultiCharacterLiteralInRange(treeNode: AntlrTreeNode) : SemanticsDiagnostic(DiagnosticSeverity.Error, treeNode.getInterval())

class EmptyClosure(treeNode: AntlrNode) : SemanticsDiagnostic(DiagnosticSeverity.Error, treeNode.getInterval())

// TODO: Correct AntlrNode instead of SourceInterval
class UnreachableElement(sourceInterval: SourceInterval) : SemanticsDiagnostic(DiagnosticSeverity.Warning, sourceInterval)

enum class DiagnosticSeverity {
    Error,
    Warning,
}