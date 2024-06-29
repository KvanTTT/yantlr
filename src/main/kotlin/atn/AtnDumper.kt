package atn

import SourceInterval
import parser.getLineColumn
import parser.stringEscapeToLiteralChars
import kotlin.collections.contains
import kotlin.collections.getOrPut
import kotlin.collections.getValue
import kotlin.collections.withIndex
import kotlin.let
import kotlin.text.any

class AtnDumper(private val lineOffsets: List<Int>?, private val lineBreak: String = "\n") {
    companion object {
        private const val INDENT = "  "
        private const val IGNORE_INDEX = -1
        private val enquoteChars = setOf('(', ')', '{', '}', '[', ']', ',', '.', ' ', '-', '\\', '/', '*')
    }

    private val visitedStates: MutableSet<State> = mutableSetOf()
    private val stateNames: MutableMap<State, String> = mutableMapOf()

    fun dump(atn: Atn): String {
        return dump { builder ->
            builder.startAppendDump(atn.modeStartStates)
            builder.startAppendDump(atn.lexerStartStates)
            builder.startAppendDump(atn.parserStartStates)
        }
    }

    fun dump(state: State): String {
        return dump { builder ->
            builder.startAppendDump(state, IGNORE_INDEX)
        }
    }

    private inline fun dump(appendFunc: (StringBuilder) -> Unit): String {
        return buildString {
            append("digraph ATN {")
            append(lineBreak)
            append(INDENT)
            append("rankdir=LR;")
            append(lineBreak)

            appendFunc(this)

            append('}')
        }
    }

    private fun StringBuilder.startAppendDump(states: List<State>) {
        states.forEachIndexed { index, state -> startAppendDump(state, if (states.size > 1) index else IGNORE_INDEX) }
    }

    private fun StringBuilder.startAppendDump(state: State, stateIndex: Int) {
        append(lineBreak)
        if (state.outTransitions.isEmpty()) {
            append(INDENT)
            append(state.getName(stateIndex))
            append(lineBreak)
        } else {
            appendDump(state, stateIndex)
        }
    }

    private fun StringBuilder.appendDump(state: State, stateIndex: Int) {
        if (!visitedStates.add(state)) return

        val stateName = state.getName(stateIndex)
        val multipleOutTransitions = state.outTransitions.size > 1
        state.outTransitions.forEachIndexed { index, transition ->
            append(INDENT)
            append(stateName)
            append(" -> ")
            append(transition.target.getName())
            append(" [label=")
            append(transition.getLabel().escapeAndEnquoteIfNeeded())
            if (multipleOutTransitions) {
                append(" taillabel=")
                append(index)
            }
            if (transition.data is EndTransitionData) {
                append(" style=dotted")
            } else if (transition.data is ErrorTransitionData) {
                append(" style=dotted color=red")
            }
            append("]")
            append(lineBreak)

            appendDump(transition.target, IGNORE_INDEX)
        }
    }

    private fun Transition<*>.getLabel(): String {
        val name = when (data) {
            is EpsilonTransitionData -> "ε"
            is IntervalTransitionData -> buildString { this.appendInterval(names = null, data.interval) }
            is SetTransitionData -> data.set.dump()
            is RuleTransitionData -> "rule(${data.rule.name})"
            is EndTransitionData -> "end(${data.rule.name})"
            is ErrorTransitionData -> "error(${data.diagnostic::class.simpleName})"
        }

        val treeNodes = if (data.antlrNodes.size > 1) {
            buildString {
                append(" {")
                for ((antlrNodeIndex, antlrNode) in data.antlrNodes.withIndex()) {
                    val interval = antlrNode.getInterval().let {
                        if (this@getLabel.data is EndTransitionData) SourceInterval(it.end(), 0) else it
                    }
                    append(interval.offset.let {
                        if (lineOffsets != null) it.getLineColumn(lineOffsets) else it
                    })
                    if (interval.length > 0) {
                        append('(')
                        append(interval.length)
                        append(')')
                    }
                    if (antlrNodeIndex < data.antlrNodes.size - 1) {
                        append(", ")
                    }
                }
                append('}')
            }
        } else {
            ""
        }

        return name + treeNodes
    }

    /*
     * If names == null then it's a lexer set, otherwise it's a parser set.
     */
    private fun IntervalSet.dump(names: Map<Int, String>? = null): String {
        return buildString {
            for ((index, range) in this@dump.intervals.withIndex()) {
                this.appendInterval(names, range)
                if (index < this@dump.intervals.size - 1) {
                    append(", ")
                }
            }
        }
    }

    private fun StringBuilder.appendInterval(names: Map<Int, String>?, interval: Interval) {
        fun appendElement(element: Int) {
            append(names?.getValue(element) ?:
                when (element) {
                    Interval.MIN -> "-∞"
                    Interval.MAX -> "+∞"
                    else -> element.toChar()
                }
            )
        }

        appendElement(interval.start)
        if (interval.start != interval.end) {
            append("..")
            appendElement(interval.end)
        }
    }

    private fun State.getName(stateIndex: Int = IGNORE_INDEX): String {
        return stateNames.getOrPut(this) {
            (toString() + (if (stateIndex == IGNORE_INDEX) "" else " [$stateIndex]")).escapeAndEnquoteIfNeeded()
        }
    }

    private fun String.escapeAndEnquoteIfNeeded(): String {
        return if (any { it in stringEscapeToLiteralChars }) {
            buildString {
                append('"')
                for (char in this@escapeAndEnquoteIfNeeded) {
                    append(stringEscapeToLiteralChars[char]?.let { "\\\\" + it } ?: char.toString())
                }
                append('"')
            }
        } else if (any { it in enquoteChars }) {
            "\"$this\""
        } else {
            this
        }
    }
}