package parser

import SourceInterval

abstract class AntlrNode {
    abstract fun getInterval(): SourceInterval?
}