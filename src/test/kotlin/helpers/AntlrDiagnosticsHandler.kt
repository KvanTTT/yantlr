package helpers

import AntlrDiagnostic

object AntlrDiagnosticsHandler : DiagnosticsHandler<AntlrDiagnostic>(
    diagnosticStartMarker = "/*❗",
    diagnosticEndMarker = "*/",
    getDiagnosticName = { it::class.simpleName!! },
    ignoredPropertyNames = setOf("severity")
)