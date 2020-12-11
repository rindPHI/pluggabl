package de.dominicsteinhoefel.pluggabl.rules

import de.dominicsteinhoefel.pluggabl.analysis.SymbolsManager
import de.dominicsteinhoefel.pluggabl.analysis.rules.SERule
import de.dominicsteinhoefel.pluggabl.expr.ConstrConverter
import de.dominicsteinhoefel.pluggabl.expr.ExprConverter
import de.dominicsteinhoefel.pluggabl.expr.NegatedConstr
import de.dominicsteinhoefel.pluggabl.expr.SymbolicExecutionState
import org.slf4j.LoggerFactory
import soot.jimple.Stmt
import soot.jimple.internal.*

object IfRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JIfStmt

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>, symbolsManager: SymbolsManager) =
        (ConstrConverter.convert((stmt as JIfStmt).condition, symbolsManager)).let { constraint ->
            (SymbolicExecutionState.merge(inpStates)).let {
                listOf(
                    it.addConstraint(NegatedConstr.create(constraint)),
                    it.addConstraint(constraint)
                )
            }
        }

    override fun toString() = "IfRule"
}

object ReturnValueRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) = stmt is JReturnStmt

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>, symbolsManager: SymbolsManager) =
        listOf(
            SymbolicExecutionState.merge(inpStates).addAssignment(
                symbolsManager.getResultVariable(),
                ExprConverter.convert((stmt as JReturnStmt).op, symbolsManager)
            )
        )

    override fun toString() = "ReturnValueRule"
}

object ReturnVoidRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) = stmt is JReturnVoidStmt

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>, symbolsManager: SymbolsManager) =
        emptyList<SymbolicExecutionState>()

    override fun toString() = "ReturnVoidRule"
}

object DummyRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JIdentityStmt || stmt is JGotoStmt

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>, symbolsManager: SymbolsManager) =
        listOf(SymbolicExecutionState.merge(inpStates))

    override fun toString() = "DummyRule"
}

object IgnoreAndWarnRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JInvokeStmt

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager
    ): List<SymbolicExecutionState> {
        LoggerFactory.getLogger(this::class.simpleName)
            .warn("Ignoring JInvokeStmt $stmt for now, have to handle appropriately soon!")
        return listOf(SymbolicExecutionState.merge(inpStates))
    }

    override fun toString() = "IgnoreAndWarnRule"
}