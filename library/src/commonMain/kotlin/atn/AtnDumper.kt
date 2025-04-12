package atn

import isPrintable
import parser.getLineColumn
import parser.stringEscapeToLiteralChars

class AtnDumper(private val lineOffsets: List<Int>?, private val lineBreak: String = "\n") {
    companion object {
        private const val INDENT = "  "
        private const val IGNORE_INDEX = -1
        private val enquoteChars = setOf('(', ')', '{', '}', '[', ']', ',', '.', ' ', '-', '\\', '/', '*', '"')
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
            }
            if (transition.data is RealTransitionData && transition.data.negationNodes.isNotEmpty()) {
                append(" color=gold")
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
            is RuleTransitionData -> "rule(${data.rule.name})"
            is EndTransitionData -> "end(${data.rule.name})"
        }

        val treeNodes = if (data is RealTransitionData && data.antlrNodes.size > 1) {
            buildString {
                append(" {")
                for ((antlrNodeIndex, antlrNode) in data.antlrNodes.withIndex()) {
                    val interval = antlrNode.getInterval()
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

        if (interval.isEmpty) {
            append('∅')
        } else {
            appendElement(interval.start)
            if (interval.start != interval.end) {
                append("..")
                appendElement(interval.end)
            }
        }
    }

    private fun State.getName(stateIndex: Int = IGNORE_INDEX): String {
        return stateNames.getOrPut(this) {
            (toString() + (if (stateIndex == IGNORE_INDEX) "" else " [$stateIndex]")).escapeAndEnquoteIfNeeded()
        }
    }

    private fun String.escapeAndEnquoteIfNeeded(): String {
        val useQuotes = any { it in stringEscapeToLiteralChars || it in enquoteChars }
        return buildString {
            if (useQuotes) {
                append('"')
            }
            for (char in this@escapeAndEnquoteIfNeeded) {
                val escapedChar = stringEscapeToLiteralChars[char]
                if (escapedChar != null) {
                    if (escapedChar != char) {
                        // If escaped char is a letter, double escaping is needed,
                        // because it's not a letter, but still a special char
                        append('\\')
                    }
                    append('\\')
                    append(escapedChar)
                } else {
                    appendPrintable(char)
                }
            }
            if (useQuotes) {
                append('"')
            }
        }
    }

    private fun StringBuilder.appendPrintable(char: Char) {
        if (char.isPrintable) {
            append(char)
        } else {
            // Avoid printing non-printable characters because they could cause incorrect Graphviz rendering,
            // and they are confusing
            append("\\\\u")
            append(char.code.toString(16).uppercase().padStart(4, '0'))
        }
    }
}