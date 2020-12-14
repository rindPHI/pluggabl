package de.dominicsteinhoefel.pluggabl.rules

import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.pluggabl.analysis.SymbolsManager
import de.dominicsteinhoefel.pluggabl.analysis.rules.SERule
import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.theories.ARRAY_FIELD
import de.dominicsteinhoefel.pluggabl.theories.HEAP_VAR
import de.dominicsteinhoefel.pluggabl.theories.STORE
import de.dominicsteinhoefel.pluggabl.theories.Select
import soot.jimple.Stmt
import soot.jimple.internal.*

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

    override fun toString() = "AssignSimpleRule"
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
                                Select.create(TypeConverter.convert(fieldRef.type)),
                                HEAP_VAR,
                                ExprConverter.convert(fieldRef.base, symbolsManager) as LocalVariable,
                                symbolsManager.getFieldSymbol(fieldRef)
                            )
                        )
                    )
                }
            }
        }

    override fun toString() = "AssignFromFieldRule"
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

    override fun toString() = "AssignToFieldRule"
}

object AssignFromArrayRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JAssignStmt && stmt.leftOp is JimpleLocal && stmt.rightOp is JArrayRef

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>, symbolsManager: SymbolsManager) =
        (stmt as JAssignStmt).let { assgnStmt ->
            (ExprConverter.convert(assgnStmt.leftOp, symbolsManager) as LocalVariable).let { locVar ->
                (assgnStmt.rightOp as JArrayRef).let { arrayRef ->
                    listOf(
                        SymbolicExecutionState.merge(inpStates).addAssignment(
                            locVar,
                            FunctionApplication(
                                Select.create(TypeConverter.convert(arrayRef.type)),
                                HEAP_VAR,
                                ExprConverter.convert(arrayRef.base, symbolsManager) as LocalVariable,
                                FunctionApplication(
                                    ARRAY_FIELD,
                                    ExprConverter.convert(arrayRef.index, symbolsManager)
                                )
                            )
                        )
                    )
                }
            }
        }

    override fun toString() = "AssignFromArrayRule"
}

object AssignToArrayRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JAssignStmt && stmt.leftOp is JArrayRef && stmt.rightOp is JimpleLocal

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>, symbolsManager: SymbolsManager) =
        (stmt as JAssignStmt).let { assgnStmt ->
            (assgnStmt.leftOp as JArrayRef).let { arrayRef ->
                (ExprConverter.convert(assgnStmt.rightOp, symbolsManager) as LocalVariable).let { locVar ->
                    listOf(
                        SymbolicExecutionState.merge(inpStates).addAssignment(
                            HEAP_VAR,
                            FunctionApplication(
                                STORE,
                                HEAP_VAR,
                                ExprConverter.convert(arrayRef.base, symbolsManager) as LocalVariable,
                                FunctionApplication(
                                    ARRAY_FIELD,
                                    ExprConverter.convert(arrayRef.index, symbolsManager)
                                ),
                                locVar
                            )
                        )
                    )
                }
            }
        }

    override fun toString() = "AssignToArrayRule"
}

object AssignFromPureVirtualInvokationRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JAssignStmt && stmt.leftOp is JimpleLocal && stmt.rightOp.let {
            it is JVirtualInvokeExpr && SymbolicExecutionAnalysis.isPureMethod(it.methodRef.toString())
        }

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>, symbolsManager: SymbolsManager) =
        (stmt as JAssignStmt).let {
            listOf(
                SymbolicExecutionState.merge(inpStates).addAssignment(
                    symbolsManager.localVariableFor(it.leftOp as JimpleLocal),
                    ExprConverter.convert(it.rightOp, symbolsManager)
                )
            )
        }

    override fun toString() = "AssignSimpleRule"
}

object AssignFromPureStaticInvokationRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JAssignStmt && stmt.leftOp is JimpleLocal && stmt.rightOp.let {
            it is JStaticInvokeExpr && SymbolicExecutionAnalysis.isPureMethod(it.methodRef.toString())
        }

    override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>, symbolsManager: SymbolsManager) =
        (stmt as JAssignStmt).let {
            listOf(
                SymbolicExecutionState.merge(inpStates).addAssignment(
                    symbolsManager.localVariableFor(it.leftOp as JimpleLocal),
                    ExprConverter.convert(it.rightOp, symbolsManager)
                )
            )
        }

    override fun toString() = "AssignSimpleRule"
}