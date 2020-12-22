package de.dominicsteinhoefel.pluggabl.rules

import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.pluggabl.analysis.SymbolsManager
import de.dominicsteinhoefel.pluggabl.analysis.rules.SERule
import de.dominicsteinhoefel.pluggabl.expr.FunctionApplication
import de.dominicsteinhoefel.pluggabl.expr.NegatedConstr
import de.dominicsteinhoefel.pluggabl.expr.SymbolicExecutionState
import de.dominicsteinhoefel.pluggabl.expr.TypeConverter
import de.dominicsteinhoefel.pluggabl.expr.converters.ConstrConverter
import de.dominicsteinhoefel.pluggabl.expr.converters.ExprConverter
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory
import de.dominicsteinhoefel.pluggabl.util.concat
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

/**
 * Note: Pure invoke statements generally do not make much sense. Those that do not
 * change the heap usually throw an exception under certain circumstances. This is
 * currently not considered and should be added in the future.
 */
object PureInvokationRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JInvokeStmt && SymbolicExecutionAnalysis.isPureMethod(stmt.invokeExpr.methodRef.toString())

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ) =
        (stmt as JInvokeStmt).let {
            listOf(
                SymbolicExecutionState.merge(inpStates)
            )
        }

    override fun toString() = "PureInvokationRule"
}

object ImpureInvokationRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JInvokeStmt && !SymbolicExecutionAnalysis.isPureMethod(stmt.invokeExpr.methodRef.toString())

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ) =
        (stmt as JInvokeStmt).invokeExpr.let { invokeExpr ->
            listOf(
                SymbolicExecutionState.merge(inpStates).addAssignment(
                    HeapTheory.HEAP_VAR,
                    FunctionApplication(
                        symbolsManager.getMethodHeapResultSymbol(invokeExpr.methodRef),
                        concat(invokeExpr.args.map { exprConverter.convert(it) })
                    )
                )
            )
        }

    override fun toString() = "ImpureInvokationRule"
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