package de.dominicsteinhoefel.symbex

import de.dominicsteinhoefel.symbex.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.symbex.expr.SymbolicExecutionState
import org.junit.Assert.*
import org.hamcrest.CoreMatchers.*
import soot.jimple.Stmt

object SymbolicExecutionTestHelper {
    fun compareLeaves(expected: List<SymbolicExecutionState>, a: SymbolicExecutionAnalysis) {
        val results = a.cfg.tails.map { it as Stmt }.map { a.getInputSESs(it) }.flatten()
        assertThat(results, `is`(expected))
    }

    fun compareLoopLeaves(expected: List<SymbolicExecutionState>, a: SymbolicExecutionAnalysis) {
        val results = a.getLoopLeafSESs().values.toList()
        assertThat(results, `is`(expected))
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