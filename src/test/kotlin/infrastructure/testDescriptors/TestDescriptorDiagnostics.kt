package infrastructure.testDescriptors

import Diagnostic
import SourceInterval

abstract class TestDescriptorDiagnostic(val arg: String, sourceInterval: SourceInterval) : Diagnostic(sourceInterval)

class UnknownPropertyDiagnostic(arg: String, sourceInterval: SourceInterval) : TestDescriptorDiagnostic(arg, sourceInterval)

class DuplicatedPropertyDiagnostic(arg: String, sourceInterval: SourceInterval) : TestDescriptorDiagnostic(arg, sourceInterval)

class DuplicatedValueDiagnostic(arg: String, sourceInterval: SourceInterval) : TestDescriptorDiagnostic(arg, sourceInterval)

class MissingPropertyDiagnostic(arg: String, sourceInterval: SourceInterval) : TestDescriptorDiagnostic(arg, sourceInterval)

