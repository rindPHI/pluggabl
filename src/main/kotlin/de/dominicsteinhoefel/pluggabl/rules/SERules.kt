package de.dominicsteinhoefel.pluggabl.analysis

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

object AssignSimpleRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JAssignStmt && stmt.leftOp is JimpleLocal && ExprConverter.isSimpleExpression(stmt.rightOp)

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>, symbolsManager: SymbolsManager) =
        (stmt as JAssignStmt).let {
            listOf(
                SymbolicExecutionState.merge(inpStates).addAssignment(
                    ExprConverter.convert(it.leftOp, symbolsManager) as LocalVariable,
                    ExprConverter.convert(it.rightOp, symbolsManager)
                )
            )
        }

    override fun toString() = "AssignRule"
}

object AssignFromFieldRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JAssignStmt && stmt.leftOp is JimpleLocal && stmt.rightOp is JInstanceFieldRef

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>, symbolsManager: SymbolsManager) =
        (stmt as JAssignStmt).let { assgnStmt ->
            (ExprConverter.convert(assgnStmt.leftOp, symbolsManager) as LocalVariable).let { locVar ->
                (assgnStmt.rightOp as JInstanceFieldRef).let { fieldRef ->
                    listOf(
                        SymbolicExecutionState.merge(inpStates).addAssignment(
                            locVar,
                            FunctionApplication(
                                Select(TypeConverter.convert(fieldRef.type)),
                                HEAP_VAR,
                                ExprConverter.convert(fieldRef.base, symbolsManager) as LocalVariable,
                                symbolsManager.getFieldSymbol(fieldRef)
                            )
                        )
                    )
                }
            }
        }

    override fun toString() = "AssignRule"
}

object AssignToFieldRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JAssignStmt && stmt.leftOp is JInstanceFieldRef && stmt.rightOp is JimpleLocal

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>, symbolsManager: SymbolsManager) =
        (stmt as JAssignStmt).let { assgnStmt ->
            (assgnStmt.leftOp as JInstanceFieldRef).let { fieldRef ->
                (ExprConverter.convert(assgnStmt.rightOp, symbolsManager) as LocalVariable).let { locVar ->
                    listOf(
                        SymbolicExecutionState.merge(inpStates).addAssignment(
                            HEAP_VAR,
                            FunctionApplication(
                                STORE,
                                HEAP_VAR,
                                ExprConverter.convert(fieldRef.base, symbolsManager) as LocalVariable,
                                symbolsManager.getFieldSymbol(fieldRef),
                                locVar
                            )
                        )
                    )
                }
            }
        }

    override fun toString() = "AssignRule"
}

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

object LeafRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JReturnStmt || stmt is JReturnVoidStmt

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>, symbolsManager: SymbolsManager) =
        emptyList<SymbolicExecutionState>()

    override fun toString() = "LeafRule"
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