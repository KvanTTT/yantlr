package atn

import parser.stringEscapeToLiteralChars
import kotlin.collections.contains
import kotlin.collections.forEach
import kotlin.collections.getOrPut
import kotlin.collections.getValue
import kotlin.collections.withIndex
import kotlin.let
import kotlin.text.any

class AtnDumper(private val printTransLocation: Boolean = false, private val lineBreak: String = "\n") {
    companion object {
        private const val INDENT = "  "
        private val enquoteChars = setOf('(', ')', '{', '}', '[', ']', ',', ' ')
    }

    private val visitedStates: MutableSet<State> = mutableSetOf()
    private val stateNames: MutableMap<State, String> = mutableMapOf()

    fun dump(atn: Atn): String {
        return dump { builder ->
            atn.modeStartStates.forEach { builder.startDump(it) }
            atn.lexerStartStates.forEach { builder.startDump(it) }
            atn.parserStartStates.forEach { builder.startDump(it) }
        }
    }

    fun dump(state: State): String {
        return dump { builder ->
            builder.startDump(state)
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

    private fun StringBuilder.startDump(state: State) {
        append(lineBreak)
        if (state.outTransitions.isEmpty()) {
            append(INDENT)
            append(state.getName())
            append(lineBreak)
        } else {
            dump(state)
        }
    }

    private fun StringBuilder.dump(state: State) {
        if (!visitedStates.add(state)) return

        val stateName = state.getName()
        val multipleTransitions = state.outTransitions.size > 1
        state.outTransitions.forEachIndexed { index, transition ->
            append(INDENT)
            append(stateName)
            append(" -> ")
            append(transition.target.getName())
            append(" [label=")
            val transitionLabel =
                when (transition) {
                    is EpsilonTransition -> "ε"
                    is SetTransition -> transition.set.dumpSet()
                    is RuleTransition -> "rule(${transition.rule.ruleNode.idToken.value!!})"
                    is EndTransition -> "end(${transition.rule.ruleNode.idToken.value!!})"
                    else -> TODO("Not implemented transition type: $transition")
                } +
                (if (multipleTransitions) " [$index]" else "")
            append(transitionLabel.escapeAndEnquoteIfNeeded())
            if (transition is EndTransition) {
                append(", style=dotted")
            }
            append("]")
            append(lineBreak)

            dump(transition.target)
        }
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

    private fun State.getName(): String {
        return stateNames.getOrPut(this) { toString().escapeAndEnquoteIfNeeded() }
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