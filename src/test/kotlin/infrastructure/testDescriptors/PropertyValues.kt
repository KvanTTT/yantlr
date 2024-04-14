package infrastructure.testDescriptors

import SourceInterval

abstract class PropertyValue(val sourceInterval: SourceInterval) {
    abstract val value: CharSequence
}

class TextPropertyValue(override val value: CharSequence, val textType: TextType, sourceInterval: SourceInterval) : PropertyValue(sourceInterval)