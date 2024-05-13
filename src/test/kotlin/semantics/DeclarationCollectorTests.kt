package semantics

import junit.framework.Assert.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

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

        declarationInfo.lexerRules.checkRules("A", "B", "C", "D", "E")
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
    fun ruleModifiers() {
        val grammar = """
            grammar test;
            Regular: 'Regular';
            fragment Fragment: 'Fragment';
            parserRule: Regular Fragment;
        """.trimIndent()

        val declarationInfo = extractDeclarationInfo(grammar)

        val regularRule = declarationInfo.lexerRules.getValue("Regular")
        assertTrue(regularRule.isLexer && !regularRule.isFragment)

        val fragmentRule = declarationInfo.lexerRules.getValue("Fragment")
        assertTrue(fragmentRule.isLexer && fragmentRule.isFragment)

        val parserRule = declarationInfo.parserRules.getValue("parserRule")
        assertTrue(!parserRule.isLexer && !parserRule.isFragment)
    }

    private fun extractDeclarationInfo(grammar: String): DeclarationsInfo {
        return GrammarPipeline.run(grammar, debugMode = true).declarationsInfo
    }

    private fun Map<String, Rule>.checkRules(vararg ruleNames: String) {
        assertEquals(ruleNames.size, size)
        onEachIndexed { ruleIndex, ruleEntry -> assertEquals(ruleNames[ruleIndex], ruleEntry.key) }
    }
}