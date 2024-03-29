package helpers.testDescriptors

import Diagnostic
import SourceInterval

class TestDescriptorDiagnostic(
    val type: TestDescriptorDiagnosticType,
    val arg: String,
    sourceInterval: SourceInterval
) : Diagnostic(sourceInterval)

enum class TestDescriptorDiagnosticType {
    UnknownProperty,
    DuplicatedProperty,
    DuplicatedValue,
    MissingProperty,
}