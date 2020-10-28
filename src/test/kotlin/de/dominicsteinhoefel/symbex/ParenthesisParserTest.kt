package de.dominicsteinhoefel.symbex

import de.dominicsteinhoefel.symbex.SymbolicExecutionTestHelper.printLeafSESs
import de.dominicsteinhoefel.symbex.SymbolicExecutionTestHelper.printSESs
import de.dominicsteinhoefel.symbex.SymbolicExecutionTestHelper.symbolicallyExecuteMethod
import de.dominicsteinhoefel.symbex.analysis.SymbolicExecutionAnalysis
import org.junit.Assert
import org.junit.Test
import soot.options.Options
import soot.toolkits.graph.UnitGraph

class ParenthesisParserTest {

    @Test
    fun testParenthesisParserAnalysis() {
        symbolicallyExecuteMethod(
            "de.dominicsteinhoefel.symbex.ParenthesisParser",
            "int parse(java.lang.Character[])",
            fun(a: SymbolicExecutionAnalysis, g: UnitGraph) {
                printLeafSESs()(a, g)
                Assert.assertEquals(5, g.tails.size)
                Assert.assertEquals(
                    "({(opParCnt_ANON_LOOP)==(0), (i_ANON_LOOP)>=(input.length)}, " +
                            "[i -> i_ANON_LOOP]++[c -> c_ANON_LOOP]++[\$stack6 -> \$stack6_ANON_LOOP]++[opParCnt -> opParCnt_ANON_LOOP]++[\$stack5 -> input.length])",
                    a.getFlowBefore(g.tails[3]).toString()
                )
            }
        )

        // TODO: Add more expressive tests.
    }

}