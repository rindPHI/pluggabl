package de.dominicsteinhoefel.pluggabl.analysis.rules

import de.dominicsteinhoefel.pluggabl.analysis.SymbolsManager
import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.theories.HEAP_VAR
import de.dominicsteinhoefel.pluggabl.theories.STORE
import de.dominicsteinhoefel.pluggabl.theories.Select
import org.slf4j.LoggerFactory
import soot.jimple.Stmt
import soot.jimple.internal.*

interface SERule {
    fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>): Boolean
    fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager
    ): List<SymbolicExecutionState>
}