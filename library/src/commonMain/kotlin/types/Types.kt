package types

import atn.Interval
import parser.AntlrNode
import declarations.Mode
import declarations.Rule

sealed class Type(val antlrNode: AntlrNode, val name: String?)

sealed class LeafType(antlrNode: AntlrNode, name: String?) : Type(antlrNode, name)

sealed class SingleChildType(antlrNode: AntlrNode, val childType: Type, name: String?) : Type(antlrNode, name)

sealed class MultipleChildrenType(antlrNode: AntlrNode, val childrenTypes: List<Type>, name: String?) : Type(antlrNode, name)

class ModeType(val mode: Mode, childType: Type) : SingleChildType(mode.modeTreeNode, childType, mode.name)

class RuleType(val rule: Rule, childType: Type) : SingleChildType(rule.treeNode, childType, rule.name)

class AtomType(val interval: Interval, antlrNode: AntlrNode, name: String?) : LeafType(antlrNode, name)

class EmptyType(val emptyNode: AntlrNode, name: String?) : LeafType(emptyNode, name)

class RuleRefType(val refRule: Rule, antlrNode: AntlrNode, name: String?) : LeafType(antlrNode, name)

class SequenceType(childrenTypes: List<Type>, antlrNode: AntlrNode, name: String?) : MultipleChildrenType(antlrNode, childrenTypes, name) {
    init {
        require(childrenTypes.size > 1)
    }
}

class UnionType(childrenTypes: List<Type>, antlrNode: AntlrNode, name: String?) : MultipleChildrenType(antlrNode, childrenTypes, name) {
    init {
        require(childrenTypes.size > 1)
    }
}

class OptionalType(childType: Type, antlrNode: AntlrNode, name: String?) : SingleChildType(antlrNode, childType, name)

sealed class IterationType(childType: Type, antlrNode: AntlrNode, name: String?) : SingleChildType(antlrNode, childType, name)

class StarIterationType(childType: Type, antlrNode: AntlrNode, name: String?) : IterationType(childType, antlrNode, name)

class PlusIterationType(childType: Type, antlrNode: AntlrNode, name: String?) : IterationType(childType, antlrNode, name)

fun Type.cloneWithNewTypeName(typeName: String): Type {
    return when (this) {
        is ModeType,
        is RuleType -> error("Should not be here")
        is AtomType -> AtomType(interval, antlrNode, typeName)
        is EmptyType -> EmptyType(emptyNode, typeName)
        is RuleRefType -> RuleRefType(refRule, antlrNode, typeName)
        is SequenceType -> SequenceType(childrenTypes, antlrNode, typeName)
        is UnionType -> UnionType(childrenTypes, antlrNode, typeName)
        is OptionalType -> OptionalType(childType, antlrNode, typeName)
        is StarIterationType -> StarIterationType(childType, antlrNode, typeName)
        is PlusIterationType -> PlusIterationType(childType, antlrNode, typeName)
    }
}

class TypesInfo(
    val lexerModeTypes: List<ModeType>,
    val lexerRuleTypes: List<RuleType>,
    val parserRuleTypes: List<RuleType>,
)