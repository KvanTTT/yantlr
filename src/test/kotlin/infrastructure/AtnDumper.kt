package infrastructure

import atn.*
import parser.stringEscapeToLiteralChars

class AtnDumper(private val printTransLocation: Boolean = false, private val lineBreak: String = "\n") {
    companion object {
        private const val INDENT = "  "
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

            atn.ruleStates.values.forEach {
                visitedStates.clear()

                append(lineBreak)
                if (it.outTransitions.isEmpty()) {
                    append(INDENT)
                    append(it.getName())
                    append(lineBreak)
                } else {
                    dump(it)
                }
            }

            append('}')
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
            append(" [label=\"")
            when (it) {
                is EpsilonTransition -> append("ε")
                is SetTransition -> appendSet(it.set)
                else -> TODO("Not implemented transition type: $it")
            }
            append("\"]")
            append(lineBreak)

            dump(it.target)
        }
    }

    /*
     * If names == null then it's a lexer set, otherwise it's a parser set.
     */
    private fun StringBuilder.appendSet(set: IntervalSet, names: Map<Int, String>? = null) {
        fun appendElement(element: Int) {
            append(names?.getValue(element)
                ?: element.toChar()
                    .let { char -> stringEscapeToLiteralChars[char]?.let { "\\\\" + it } ?: char.toString() }
            )
        }

        for ((index, range) in set.intervals.withIndex()) {
            appendElement(range.start)
            if (range.start != range.end) {
                append("..")
                appendElement(range.end)
            }
            if (index < set.intervals.size - 1) {
                append(", ")
            }
        }
    }

    private fun State.getName(): String {
        return stateNames.getOrPut(this) { if (this is RuleState) rule.name else "s${number}" }
    }
}