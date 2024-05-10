package infrastructure

import atn.*
import parser.stringEscapeToLiteralChars

class AtnDumper(private val printTransLocation: Boolean = false, private val lineBreak: String = "\n") {
    companion object {
        private const val INDENT = "  "
        private val enquoteChars = setOf('(', ')', '{', '}', ',')
    }

    private val visitedStates: MutableSet<State> = mutableSetOf()
    private val stateNames: MutableMap<State, String> = mutableMapOf()

    fun dump(atn: Atn): String {
        return buildString {
            append("digraph ATN {")
            append(lineBreak)
            append(INDENT)
            append("rankdir=LR;")
            append(lineBreak)

            atn.modeStartStates.forEach { startDump(it) }
            atn.lexerStartStates.forEach { startDump(it) }
            atn.parserStartStates.forEach { startDump(it) }

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
        state.outTransitions.forEach {
            append(INDENT)
            append(stateName)
            append(" -> ")
            append(it.target.getName())
            append(" [label=")
            val transitionLabel = when (it) {
                is EpsilonTransition -> "ε"
                is SetTransition -> it.set.dumpSet()
                is RuleTransition -> "rule(${it.rule.ruleNode.idToken.value!!})"
                is EndTransition -> "end(${it.rule.ruleNode.idToken.value!!})"
                else -> TODO("Not implemented transition type: $it")
            }
            append(transitionLabel.escapeAndEnquoteIfNeeded())
            if (it is EndTransition) {
                append(", style=dotted")
            }
            append("]")
            append(lineBreak)

            dump(it.target)
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