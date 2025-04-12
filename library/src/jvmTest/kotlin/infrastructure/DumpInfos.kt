package infrastructure

import InfoWithSourceInterval
import atn.Atn
import atn.AtnDumper
import infrastructure.testDescriptors.PropertyValue

abstract class DumpInfo(val format: String, val propertyValue: PropertyValue) : InfoWithSourceInterval(propertyValue.sourceInterval) {
    abstract fun getDump(lineBreak: String): String
}

class AtnDumpInfo(val atn: Atn, val lineOffsets: List<Int>, propertyValue: PropertyValue) : DumpInfo("dot", propertyValue) {
    override fun getDump(lineBreak: String): String = AtnDumper(lineOffsets, lineBreak = lineBreak).dump(atn)
}