package de.dominicsteinhoefel.pluggabl.test

import de.dominicsteinhoefel.pluggabl.test.SymbolicExecutionTestHelper.printLeafSESs
import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import org.junit.Assert
import org.junit.Test
import soot.jimple.Stmt

class ParenthesisParserTest {

    @Test
    fun testParenthesisParserAnalysis() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.ParenthesisParser",
            "int parse(java.lang.Character[])"
        )
        val graph = analysis.cfg

        analysis.symbolicallyExecute()

        printLeafSESs(analysis)

        TODO("Test outcome")
    }

}