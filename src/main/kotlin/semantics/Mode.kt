package semantics

import parser.ModeNode

class Mode(val modeTreeNode: ModeNode?, val rules: Map<String, Rule>)