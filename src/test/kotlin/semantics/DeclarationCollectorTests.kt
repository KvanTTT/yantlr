package semantics

import declarations.DeclarationsInfo
import declarations.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object DeclarationCollectorTests {
    @Test
    fun lexerModes() {
        val grammar = """
            lexer grammar test;
            A: 'A';
            B: 'B';

            mode Custom;
            C: 'C';
            D: 'D';

            mode DEFAULT_MODE;
            E: 'E';
        """.trimIndent()

        val declarationInfo = extractDeclarationInfo(grammar)
        assertEquals(2, declarationInfo.lexerModes.size)

        declarationInfo.lexerModes.onEachIndexed { index, entry ->
            when (index) {
                0 -> {
                    assertEquals("DEFAULT_MODE", entry.key)
                    entry.value.rules.checkRules("A", "B", "E")
                }
                1 -> {
                    assertEquals("Custom", entry.key)
                    entry.value.rules.checkRules("C", "D")
                }
                else -> throw IllegalStateException("Unexpected index: $index")
            }
        }

        declarationInfo.lexerRules.checkRules("A", "B", "E", "C", "D")
    }

    @Test
    fun parserRules() {
        val grammar = """
            parser grammar test;
            x: A;
            y: B;
            A: 'A';
            B: 'B';
        """.trimIndent()

        val declarationInfo = extractDeclarationInfo(grammar)

        declarationInfo.parserRules.checkRules("x", "y")
        declarationInfo.lexerModes.values.single().rules.checkRules("A", "B")
        declarationInfo.lexerRules.checkRules("A", "B")
    }

    @Test
    fun ruleProperties() {
        val grammar = """
            grammar test;
            Regular: 'Regular';
            Recursive: '{' Recursive '}';
            Recursive2: Regular;
            fragment Fragment: 'Fragment';
            parserRule: Regular Fragment;
        """.trimIndent()

        val declarationInfo = extractDeclarationInfo(grammar)

        fun checkRule(ruleName: String, check: (Rule) -> Boolean) {
            val rulesList = if (ruleName.first().isUpperCase()) declarationInfo.lexerRules else declarationInfo.parserRules
            assertTrue(check(rulesList.getValue(ruleName)))
        }

        checkRule("Regular") { it.isLexer && !it.isFragment && !it.isRecursive }
        checkRule("Recursive") { it.isRecursive }
        checkRule("Recursive2") { it.isRecursive }
        checkRule("Fragment") { it.isLexer && it.isFragment }
        checkRule("parserRule") { !it.isLexer }
    }

    private fun extractDeclarationInfo(grammar: String): DeclarationsInfo {
        return GrammarPipeline.run(grammar, debugMode = true).declarationsInfo
    }

    private fun Map<String, Rule>.checkRules(vararg ruleNames: String) {
        assertEquals(ruleNames.size, size)
        onEachIndexed { ruleIndex, ruleEntry -> assertEquals(ruleNames[ruleIndex], ruleEntry.key) }
    }
}