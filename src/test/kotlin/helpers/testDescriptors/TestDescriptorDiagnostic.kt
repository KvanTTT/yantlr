package helpers.testDescriptors

import SourceInterval

class TestDescriptorDiagnostic(val type: TestDescriptorDiagnosticType, val arg: String, val sourceInterval: SourceInterval)

enum class TestDescriptorDiagnosticType {
    UnknownProperty,
    DuplicatedProperty,
    DuplicatedValue,
    MissingProperty,
}