package infrastructure

import AntlrDiagnostic
import Diagnostic
import InfoWithSourceInterval
import infrastructure.testDescriptors.TestDescriptorDiagnostic

class InfoWithDescriptor<T : InfoWithSourceInterval>(val info: T, val descriptor: EmbeddedInfoDescriptor<T>) {
    operator fun component1(): T = info

    operator fun component2(): EmbeddedInfoDescriptor<T> = descriptor
}

abstract class EmbeddedInfoDescriptor<T : InfoWithSourceInterval>

abstract class DiagnosticInfoDescriptor<T : Diagnostic>(
    val startMarker: String,
    val endMarker: String,
    val getName: (T) -> String,
    val ignoredPropertyNames: Set<String>,
) : EmbeddedInfoDescriptor<T>()

object AntlrDiagnosticInfoDescriptor : DiagnosticInfoDescriptor<AntlrDiagnostic>(
    startMarker = "/*❗",
    endMarker = "*/",
    getName = { it::class.simpleName!! },
    ignoredPropertyNames = setOf("severity"),
)

object TestDescriptorDiagnosticInfoDescriptor : DiagnosticInfoDescriptor<TestDescriptorDiagnostic>(
    startMarker = "<!--❌",
    endMarker = "-->",
    getName = { it.type.name },
    ignoredPropertyNames = setOf("type"),
)

object DumpInfoDescriptor : EmbeddedInfoDescriptor<InfoWithSourceInterval>()