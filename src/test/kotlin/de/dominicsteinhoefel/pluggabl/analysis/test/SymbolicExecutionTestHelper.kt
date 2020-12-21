package de.dominicsteinhoefel.pluggabl.analysis.test

import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.pluggabl.expr.LocalVariable
import de.dominicsteinhoefel.pluggabl.expr.SymbolicExecutionState
import de.dominicsteinhoefel.pluggabl.expr.SymbolicExpression
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.*
import soot.jimple.Stmt

object SymbolicExecutionTestHelper {
    fun compareResultForVariable(expected: SymbolicExpression, state: SymbolicExecutionState, variable: LocalVariable) {
        state.store.elementaries().firstOrNull { it.lhs == variable }?.rhs
            .also { assertNotNull(it) }
            ?.also { assertEquals(expected, it) }
    }

    fun compareLeaveInputs(expected: List<SymbolicExecutionState>, a: SymbolicExecutionAnalysis) {
        val results = a.cfg.tails.map { it as Stmt }.map { a.getInputSESs(it) }.flatten()
        assertThat(results, `is`(expected))
    }

    fun compareLeaves(expected: List<SymbolicExecutionState>, a: SymbolicExecutionAnalysis) {
        val results = a.getLeavesWithOutputSESs().values.flatten()

        assertEquals(expected.size, results.size)
        for (i in expected.indices) {
            assertEquals(expected[i], results[i])
        }
    }

    fun compareLoopLeaves(expected: List<SymbolicExecutionState>, a: SymbolicExecutionAnalysis) {
        val results = a.getLoopLeafSESs().values.toList()

        assertEquals(expected.size, results.size)
        for (i in expected.indices) {
            assertEquals(expected[i], results[i])
        }
    }

    fun printSESs(a: SymbolicExecutionAnalysis) {
        for (node in a.cfg) {
            println("Node \"$node\":")
            println("Input States:  ${a.getInputSESs(node as Stmt).joinToString(", ")}")
            println("Output States: ${a.getOutputSESs(node).joinToString(", ")}\n")
        }
    }

    fun printLeafSESs(a: SymbolicExecutionAnalysis) {
        for (node in a.cfg.tails) {
            println("Node \"$node\":")
            println(a.getInputSESs(node as Stmt).joinToString(", "))
            println()
        }
    }
}