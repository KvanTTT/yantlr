package infrastructure

import InfoWithSourceInterval
import infrastructure.testDescriptors.PropertyValue

abstract class DumpInfo(val dump: String, val format: String, propertyValue: PropertyValue) : InfoWithSourceInterval(propertyValue.sourceInterval)

class AtnDumpInfo(dump: String, propertyValue: PropertyValue) : DumpInfo(dump, "dot", propertyValue)