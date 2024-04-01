package infrastructure.testDescriptors

import SourceInterval

abstract class PropertyValue(val sourceInterval: SourceInterval) {
    abstract val value: CharSequence
}