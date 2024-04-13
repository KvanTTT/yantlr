package infrastructure

import AntlrDiagnostic
import Diagnostic
import SourceInterval
import infrastructure.testDescriptors.TestDescriptorDiagnostic
import parser.getLineColumn
import parser.getLineOffsets
import parser.stringEscapeToLiteralChars
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

object InfoEmbedder {
    fun embedDiagnostics(extractionResult: ExtractionResult, embeddedInfos: List<Diagnostic>): String {
        return embed(extractionResult, embeddedInfos.map {
            val descriptor = when (it) {
                is AntlrDiagnostic -> AntlrDiagnosticInfoDescriptor
                is TestDescriptorDiagnostic -> TestDescriptorDiagnosticInfoDescriptor
                else -> error("Unknown diagnostic type")
            }
            @Suppress("UNCHECKED_CAST")
            InfoWithDescriptor(it, descriptor as EmbeddedInfoDescriptor<Diagnostic>)
        })
    }

    fun embed(extractionResult: ExtractionResult, embeddedInfos: List<InfoWithDescriptor<*>>): String {
        data class InfoWithOffset(val infoWithDescriptor: InfoWithDescriptor<*>, val start: Boolean)

        if (embeddedInfos.isEmpty()) return extractionResult.refinedInput

        val offsetToInfoMap = linkedMapOf<Int, MutableList<InfoWithOffset>>()

        fun getInfosWithOffset(offset: Int): MutableList<InfoWithOffset> =
            offsetToInfoMap[offset] ?: run {
                mutableListOf<InfoWithOffset>().also { offsetToInfoMap[offset] = it }
            }

        for (info in embeddedInfos.sortedBy { it.info.sourceInterval.offset }) {
            val interval = info.info.sourceInterval
            getInfosWithOffset(interval.offset).add(InfoWithOffset(info, start = true))
            if (info.descriptor is DiagnosticInfoDescriptor<*>) {
                getInfosWithOffset(interval.end()).add(InfoWithOffset(info, start = false))
            }
        }

        val input = extractionResult.refinedInput
        val lineOffsets by lazy(LazyThreadSafetyMode.NONE) { input.getLineOffsets() }

        var lastOffset = 0
        return buildString {
            for ((offset, infosWithOffset) in offsetToInfoMap) {
                append(input.subSequence(lastOffset, offset))

                for (infoWithOffset in infosWithOffset) {
                    val infoWithDescriptor = infoWithOffset.infoWithDescriptor
                    val descriptor = infoWithDescriptor.descriptor
                    if (descriptor is DiagnosticInfoDescriptor<*>) {
                        append(descriptor.startMarker)
                        if (infoWithOffset.start) {
                            @Suppress("UNCHECKED_CAST")
                            val actualDiagnosticsName =
                                (descriptor as DiagnosticInfoDescriptor<Diagnostic>).getName(
                                    infoWithDescriptor.info as Diagnostic
                                )
                            append(actualDiagnosticsName)

                            val expectedDiagnostic =
                                extractionResult.diagnostics[offset]?.single { it.name == actualDiagnosticsName }
                            if (expectedDiagnostic?.args != null) {
                                appendArgs(infoWithDescriptor, lineOffsets)
                            }
                        }
                        append(descriptor.endMarker)
                    } else {
                        // TODO: implement dumps embedding
                    }
                }

                lastOffset = offset
            }

            append(input.subSequence(lastOffset, input.length))
        }
    }

    private fun StringBuilder.appendArgs(infoWithDescriptor: InfoWithDescriptor<*>, lineOffsets: List<Int>) {
        val (info, descriptor) = infoWithDescriptor
        descriptor as DiagnosticInfoDescriptor<*>

        val nameToPropertyMap = info::class.memberProperties.associateBy { it.name }
        for (member in info::class.primaryConstructor!!.parameters) {
            @Suppress("UNCHECKED_CAST")
            val property = nameToPropertyMap[member.name] as KProperty1<Any, *>
            if (member.name.let { it == "sourceInterval" } || descriptor.ignoredPropertyNames.contains(member.name)) continue

            append(' ')

            val normalizedValue = when (val value = property.get(info)) {
                is SourceInterval -> value.offset.getLineColumn(lineOffsets)
                else -> value
            }
            val valueString = normalizedValue.toString()
            if (valueString.any { stringEscapeToLiteralChars.containsKey(it) }) {
                append('"')
                valueString.forEach {
                    if (stringEscapeToLiteralChars.containsKey(it)) append('\\')
                    append(it)
                }
                append('"')
            } else {
                append(valueString)
            }
        }
    }
}