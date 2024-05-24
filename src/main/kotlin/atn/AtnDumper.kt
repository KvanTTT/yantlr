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
        private val enquoteChars = setOf('(', ')', '{', '}', '[', ']', ',', '.', ' ')
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
            if (transition is EndTransition) {
                append(" style=dotted")
            }
            append("]")
            append(lineBreak)

            appendDump(transition.target, IGNORE_INDEX)
        }
    }

    private fun Transition.getLabel(): String {
        val name = when (this) {
            is EpsilonTransition -> "ε"
            is SetTransition -> set.dumpSet()
            is RuleTransition -> "rule(${rule.ruleNode.idToken.value!!})"
            is EndTransition -> "end(${rule.ruleNode.idToken.value!!})"
            else -> TODO("Not implemented transition type: $this")
        }

        val treeNodes = if (treeNodes.size > 1) {
            buildString {
                append(" {")
                for ((treeNodeIndex, treeNode) in treeNodes.withIndex()) {
                    val interval = treeNode.getInterval().let {
                        if (this@getLabel is EndTransition) SourceInterval(it.end(), 0) else it
                    }
                    append(interval.offset.let {
                        if (lineOffsets != null) it.getLineColumn(lineOffsets) else it
                    })
                    if (interval.length > 0) {
                        append('(')
                        append(interval.length)
                        append(')')
                    }
                    if (treeNodeIndex < treeNodes.size - 1) {
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
    private fun IntervalSet.dumpSet(names: Map<Int, String>? = null): String {
        return buildString {
            fun appendElement(element: Int) {
                append(names?.getValue(element) ?: element.toChar())
            }

            for ((index, range) in this@dumpSet.intervals.withIndex()) {
                appendElement(range.start)
                if (range.start != range.end) {
                    append("..")
                    appendElement(range.end)
                }
                if (index < this@dumpSet.intervals.size - 1) {
                    append(", ")
                }
            }
        }
    }

    private fun State.getName(stateIndex: Int = IGNORE_INDEX): String {
        return stateNames.getOrPut(this) {
            (toString() + (if (stateIndex == IGNORE_INDEX) "" else " [$stateIndex]")).escapeAndEnquoteIfNeeded()
        }
    }

    private fun String.escapeAndEnquoteIfNeeded(): String {
        val enquote = any { it in enquoteChars }
        return if (any { it in stringEscapeToLiteralChars }) {
            buildString {
                if (enquote) append('"')
                for (char in this@escapeAndEnquoteIfNeeded) {
                    append(stringEscapeToLiteralChars[char]?.let { "\\\\" + it } ?: char.toString())
                }
                if (enquote) append('"')
            }
        } else if (enquote) {
            "\"$this\""
        } else {
            this
        }
    }
}