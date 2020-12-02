package de.dominicsteinhoefel.symbex

import de.dominicsteinhoefel.symbex.SymbolicExecutionTestHelper.printLeafSESs
import de.dominicsteinhoefel.symbex.analysis.SymbolicExecutionAnalysis
import org.junit.Assert
import org.junit.Test
import soot.jimple.Stmt

class ParenthesisParserTest {

    @Test
    fun testParenthesisParserAnalysis() {
        val analysis = SymbolicExecutionAnalysis(
            "de.dominicsteinhoefel.symbex.ParenthesisParser",
            "int parse(java.lang.Character[])"
        )
        val graph = analysis.cfg

        analysis.symbolicallyExecute()

        printLeafSESs(analysis)
        Assert.assertEquals(5, graph.tails.size)
        Assert.assertEquals(
            "({(opParCnt_ANON_LOOP)==(0), (i_ANON_LOOP)>=(input.length)}, " +
                    "[i -> i_ANON_LOOP]++[c -> c_ANON_LOOP]++[\$stack6 -> \$stack6_ANON_LOOP]++[opParCnt -> opParCnt_ANON_LOOP]++[\$stack5 -> input.length])",
            analysis.getOutputSESs(graph.tails[3] as Stmt)[0].toString()
        )

        // TODO: Add more expressive tests.
    }

}