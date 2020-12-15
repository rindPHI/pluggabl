package de.dominicsteinhoefel.pluggabl.analysis.rules

import de.dominicsteinhoefel.pluggabl.analysis.SymbolsManager
import de.dominicsteinhoefel.pluggabl.expr.ExprConverter
import de.dominicsteinhoefel.pluggabl.expr.TypeConverter
import de.dominicsteinhoefel.pluggabl.expr.ConstrConverter
import de.dominicsteinhoefel.pluggabl.expr.SymbolicExecutionState
import soot.jimple.Stmt

interface SERule {
    fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>): Boolean

    fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ): List<SymbolicExecutionState>
}