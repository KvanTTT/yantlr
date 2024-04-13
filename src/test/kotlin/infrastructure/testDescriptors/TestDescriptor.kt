package infrastructure.testDescriptors

class TestDescriptor(
    val name: String,
    val notes: List<PropertyValue>,
    val grammars: List<PropertyValue>,
    val atn: PropertyValue?,
    val input: List<PropertyValue>,
)