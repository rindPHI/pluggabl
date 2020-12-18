package de.dominicsteinhoefel.pluggabl.rules

import de.dominicsteinhoefel.pluggabl.analysis.SymbolsManager
import de.dominicsteinhoefel.pluggabl.analysis.rules.SERule
import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.expr.converters.ConstrConverter
import de.dominicsteinhoefel.pluggabl.expr.converters.ExprConverter
import org.slf4j.LoggerFactory
import soot.jimple.Stmt
import soot.jimple.internal.*

object IfRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JIfStmt

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ) =
        (constrConverter.convert((stmt as JIfStmt).condition)).let { constraint ->
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

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ) =
        listOf(
            SymbolicExecutionState.merge(inpStates).addAssignment(
                symbolsManager.getResultVariable(),
                exprConverter.convert((stmt as JReturnStmt).op)
            )
        )

    override fun toString() = "ReturnValueRule"
}

object ReturnVoidRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) = stmt is JReturnVoidStmt

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ) =
        emptyList<SymbolicExecutionState>()

    override fun toString() = "ReturnVoidRule"
}

object DummyRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JIdentityStmt || stmt is JGotoStmt

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ) =
        listOf(SymbolicExecutionState.merge(inpStates))

    override fun toString() = "DummyRule"
}

object IgnoreAndWarnRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JInvokeStmt

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ): List<SymbolicExecutionState> {
        LoggerFactory.getLogger(this::class.simpleName)
            .warn("Ignoring JInvokeStmt $stmt for now, have to handle appropriately soon!")
        return listOf(SymbolicExecutionState.merge(inpStates))
    }

    override fun toString() = "IgnoreAndWarnRule"
}