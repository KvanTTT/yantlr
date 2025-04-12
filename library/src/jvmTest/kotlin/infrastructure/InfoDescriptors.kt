package infrastructure

import AntlrDiagnostic
import Diagnostic
import InfoWithSourceInterval
import infrastructure.testDescriptors.TestDescriptorDiagnostic

abstract class EmbeddedInfoDescriptor<T : InfoWithSourceInterval>

abstract class DiagnosticInfoDescriptor<T : Diagnostic>(
    val startMarker: String,
    val endMarker: String,
    val ignoredPropertyNames: Set<String>,
) : EmbeddedInfoDescriptor<T>()

object AntlrDiagnosticInfoDescriptor : DiagnosticInfoDescriptor<AntlrDiagnostic>(
    startMarker = "/*❗",
    endMarker = "*/",
    ignoredPropertyNames = setOf("severity"),
)

object TestDescriptorDiagnosticInfoDescriptor : DiagnosticInfoDescriptor<TestDescriptorDiagnostic>(
    startMarker = "<!--❌",
    endMarker = "-->",
    ignoredPropertyNames = emptySet(),
)

object DumpInfoDescriptor : EmbeddedInfoDescriptor<DumpInfo>()