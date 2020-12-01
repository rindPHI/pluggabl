package de.dominicsteinhoefel.symbex.analysis

import de.dominicsteinhoefel.symbex.expr.*
import org.slf4j.LoggerFactory
import soot.jimple.Stmt
import soot.jimple.internal.*

interface SERule {
    fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>): Boolean
    fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>): List<SymbolicExecutionState>
}

object AssignRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JAssignStmt

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        (stmt as JAssignStmt).let {
            listOf(
                SymbolicExecutionState.merge(inpStates).addAssignment(
                    ExprConverter.convert(it.leftOp) as Symbol,
                    ExprConverter.convert(it.rightOp)
                )
            )
        }

    override fun toString() = "AssignRule"
}

object IfRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JIfStmt

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        (ConstrConverter.convert((stmt as JIfStmt).condition)).let { constraint ->
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
        stmt is JReturnStmt

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        emptyList<SymbolicExecutionState>()

    override fun toString() = "LeafRule"
}

object DummyRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JIdentityStmt || stmt is JGotoStmt

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        listOf(SymbolicExecutionState.merge(inpStates))

    override fun toString() = "DummyRule"
}

object IgnoreAndWarnRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JInvokeStmt

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>): List<SymbolicExecutionState> {
        LoggerFactory.getLogger(this::class.simpleName)
            .warn("Ignoring JInvokeStmt $stmt for now, have to handle appropriately soon!")
        return listOf(SymbolicExecutionState.merge(inpStates))
    }

    override fun toString() = "IgnoreAndWarnRule"
}