package infrastructure

import SourceInterval

data class DiagnosticInfo(val name: String, val args: List<String>?, val location: SourceInterval)