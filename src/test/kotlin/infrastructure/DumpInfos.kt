package infrastructure

import InfoWithSourceInterval
import atn.Atn
import atn.AtnDumper
import infrastructure.testDescriptors.PropertyValue

abstract class DumpInfo(val format: String, val propertyValue: PropertyValue) : InfoWithSourceInterval(propertyValue.sourceInterval) {
    abstract fun getDump(lineBreak: String): String
}

class AtnDumpInfo(val atn: Atn, propertyValue: PropertyValue) : DumpInfo("dot", propertyValue) {
    override fun getDump(lineBreak: String): String = AtnDumper(lineBreak = lineBreak).dump(atn)
}