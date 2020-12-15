package de.dominicsteinhoefel.pluggabl.rules

import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.pluggabl.analysis.SymbolsManager
import de.dominicsteinhoefel.pluggabl.analysis.rules.SERule
import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.theories.*
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory.ARRAY_FIELD
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory.ARRAY_LENGTH
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory.HEAP_VAR
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory.STORE
import soot.jimple.Stmt
import soot.jimple.internal.*

object AssignSimpleRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JAssignStmt && stmt.leftOp is JimpleLocal && ExprConverter.isSimpleExpression(stmt.rightOp)

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ) =
        (stmt as JAssignStmt).let {
            listOf(
                SymbolicExecutionState.merge(inpStates).addAssignment(
                    exprConverter.convert(it.leftOp) as LocalVariable,
                    exprConverter.convert(it.rightOp)
                )
            )
        }

    override fun toString() = "AssignSimpleRule"
}

object AssignFromFieldRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JAssignStmt && stmt.leftOp is JimpleLocal && stmt.rightOp is JInstanceFieldRef

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ) =
        (stmt as JAssignStmt).let { assgnStmt ->
            (exprConverter.convert(assgnStmt.leftOp) as LocalVariable).let { locVar ->
                (assgnStmt.rightOp as JInstanceFieldRef).let { fieldRef ->
                    listOf(
                        SymbolicExecutionState.merge(inpStates).addAssignment(
                            locVar,
                            FunctionApplication(
                                HeapTheory.Select.create(typeConverter.convert(fieldRef.type)),
                                HEAP_VAR,
                                exprConverter.convert(fieldRef.base) as LocalVariable,
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

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ) =
        (stmt as JAssignStmt).let { assgnStmt ->
            (assgnStmt.leftOp as JInstanceFieldRef).let { fieldRef ->
                (exprConverter.convert(assgnStmt.rightOp) as LocalVariable).let { locVar ->
                    listOf(
                        SymbolicExecutionState.merge(inpStates).addAssignment(
                            HEAP_VAR,
                            FunctionApplication(
                                STORE,
                                HEAP_VAR,
                                exprConverter.convert(fieldRef.base) as LocalVariable,
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

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ) =
        (stmt as JAssignStmt).let { assgnStmt ->
            (exprConverter.convert(assgnStmt.leftOp) as LocalVariable).let { locVar ->
                (assgnStmt.rightOp as JArrayRef).let { arrayRef ->
                    listOf(
                        SymbolicExecutionState.merge(inpStates).addAssignment(
                            locVar,
                            FunctionApplication(
                                HeapTheory.Select.create(typeConverter.convert(arrayRef.type)),
                                HEAP_VAR,
                                exprConverter.convert(arrayRef.base) as LocalVariable,
                                FunctionApplication(
                                    ARRAY_FIELD,
                                    exprConverter.convert(arrayRef.index)
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

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ) =
        (stmt as JAssignStmt).let { assgnStmt ->
            (assgnStmt.leftOp as JArrayRef).let { arrayRef ->
                (exprConverter.convert(assgnStmt.rightOp) as LocalVariable).let { locVar ->
                    listOf(
                        SymbolicExecutionState.merge(inpStates).addAssignment(
                            HEAP_VAR,
                            FunctionApplication(
                                STORE,
                                HEAP_VAR,
                                exprConverter.convert(arrayRef.base) as LocalVariable,
                                FunctionApplication(
                                    ARRAY_FIELD,
                                    exprConverter.convert(arrayRef.index)
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

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ) =
        (stmt as JAssignStmt).let {
            listOf(
                SymbolicExecutionState.merge(inpStates).addAssignment(
                    symbolsManager.localVariableFor(it.leftOp as JimpleLocal),
                    exprConverter.convert(it.rightOp)
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

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ) =
        (stmt as JAssignStmt).let {
            listOf(
                SymbolicExecutionState.merge(inpStates).addAssignment(
                    symbolsManager.localVariableFor(it.leftOp as JimpleLocal),
                    exprConverter.convert(it.rightOp)
                )
            )
        }

    override fun toString() = "AssignSimpleRule"
}

object AssignArrayLengthRule : SERule {
    override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
        stmt is JAssignStmt && stmt.leftOp is JimpleLocal && stmt.rightOp is JLengthExpr

    override fun apply(
        stmt: Stmt,
        inpStates: List<SymbolicExecutionState>,
        symbolsManager: SymbolsManager,
        typeConverter: TypeConverter,
        exprConverter: ExprConverter,
        constrConverter: ConstrConverter
    ) =
        (stmt as JAssignStmt).let { assgmStmt ->
            (stmt.rightOp as JLengthExpr).let { lengthExpr ->
                listOf(
                    SymbolicExecutionState.merge(inpStates).addAssignment(
                        exprConverter.convert(assgmStmt.leftOp) as LocalVariable,
                        FunctionApplication(ARRAY_LENGTH, exprConverter.convert(lengthExpr.op))
                    )
                )
            }
        }

    override fun toString() = "AssignArrayLengthRule"
}