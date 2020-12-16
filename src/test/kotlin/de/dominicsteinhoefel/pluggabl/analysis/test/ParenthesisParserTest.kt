package de.dominicsteinhoefel.pluggabl.analysis.test

import de.dominicsteinhoefel.pluggabl.analysis.test.SymbolicExecutionTestHelper.printLeafSESs
import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import org.junit.Test

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