package infrastructure

import infrastructure.testDescriptors.TestDescriptorDiagnostic

object TestDescriptorDiagnosticsHandler : DiagnosticsHandler<TestDescriptorDiagnostic>(
    diagnosticStartMarker = "<!--❌",
    diagnosticEndMarker = "-->",
    getDiagnosticName = { it.type.name },
    ignoredPropertyNames = setOf("type")
)