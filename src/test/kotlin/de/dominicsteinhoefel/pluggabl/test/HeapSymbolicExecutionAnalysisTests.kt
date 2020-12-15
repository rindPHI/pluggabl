package de.dominicsteinhoefel.pluggabl.test

import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.simplification.SymbolicExpressionSimplifier
import de.dominicsteinhoefel.pluggabl.test.SymbolicExecutionTestHelper.printSESs
import de.dominicsteinhoefel.pluggabl.theories.ARRAY_FIELD
import de.dominicsteinhoefel.pluggabl.theories.HEAP_VAR
import de.dominicsteinhoefel.pluggabl.theories.STORE
import de.dominicsteinhoefel.pluggabl.theories.Select
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
import soot.jimple.Stmt

class HeapSymbolicExecutionAnalysisTests {
    @Test
    fun testSimpleTwoBranchedMethodWithMergeFieldAccess() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.HeapAccess",
            "void simpleTwoBranchedMethodWithMergeFieldAccess()"
        )

        analysis.symbolicallyExecute()

        Assert.assertEquals(1, analysis.cfg.tails.size)

        val leafState = analysis.getInputSESs(analysis.cfg.tails[0] as Stmt)[0].also {
            Assert.assertEquals(
                true,
                it.constraints.isEmpty()
            )
        }

        val thisVar = analysis.getLocal("this")

        val selectTerm = FunctionApplication(
            Select.create(INT_TYPE),
            HEAP_VAR,
            thisVar,
            analysis.symbolsManager.getFieldSymbol("de.dominicsteinhoefel.pluggabl.testcase.HeapAccess", "test")!!
        )

        val evaluatedExpression =
            SymbolicExpressionSimplifier.simplify(StoreApplExpression.create(leafState.store, selectTerm))

        // Expression should contain a conditional term with constraint "this.input == 42". We consider both cases.
        val selectInput = FunctionApplication(
            Select.create(INT_TYPE),
            HEAP_VAR,
            thisVar,
            analysis.symbolsManager.getFieldSymbol("de.dominicsteinhoefel.pluggabl.testcase.HeapAccess", "input")!!
        )

        val condition = EqualityConstr.create(selectInput, IntValue(42))

        fun replaceConditionWith(newCondition: SymbolicConstraint) =
            fun(e: SymbolicExpression) =
                if (e is ConditionalExpression && e.condition == condition)
                    ConditionalExpression.create(
                        newCondition,
                        e.vThen,
                        e.vElse
                    )
                else e

        val expr1 = SymbolicExpressionSimplifier.simplify(
            evaluatedExpression.accept(ExpressionReplacer(replaceConditionWith(True)))
        )

        val expr2 = SymbolicExpressionSimplifier.simplify(
            evaluatedExpression.accept(ExpressionReplacer(replaceConditionWith(False)))
        )

        Assert.assertEquals(AdditionExpr(AdditionExpr(selectInput, IntValue(2)), IntValue(4)), expr1)
        Assert.assertEquals(AdditionExpr(AdditionExpr(selectInput, IntValue(3)), IntValue(4)), expr2)
    }

    @Test
    fun testSimpleArrayAccess() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.HeapAccess",
            "int simpleArrayAccess(java.lang.Integer[])"
        )

        analysis.symbolicallyExecute()
        printSESs(analysis)

        val intValueSym = analysis.symbolsManager.getMethodResultSymbol(
            "java.lang.Integer", "int intValue()"
        )!!

        val valueOfSym = analysis.symbolsManager.getMethodResultSymbol(
            "java.lang.Integer", "java.lang.Integer valueOf(int)"
        )!!

        val arrVar = analysis.getLocal("arr")
        val heapVar = analysis.getLocal("heap")
        val resultVar = analysis.getLocal("result")
        val integerType = ReferenceType("java.lang.Integer")

        fun makeSelectTerm(v: Int) = FunctionApplication(
            Select.create(integerType), heapVar, arrVar, FunctionApplication(ARRAY_FIELD, IntValue(v))
        )

        val expectedResultValue = AdditionExpr(
            FunctionApplication(
                intValueSym,
                FunctionApplication(
                    valueOfSym,
                    AdditionExpr(
                        FunctionApplication(intValueSym, makeSelectTerm(1)),
                        FunctionApplication(intValueSym, makeSelectTerm(2))
                    )
                )
            ), IntValue(1)
        )

        val resultStores = analysis.getOutputSESs(
            analysis.cfg.tails.also { assertEquals(1, it.size) }[0] as Stmt
        ).also { assertEquals(1, it.size) }[0].store.toElementaryStores()

        val actualResultValue =
            resultStores.filter { it.lhs == resultVar }.also { assertEquals(1, it.size) }[0].rhs

        assertEquals(expectedResultValue, actualResultValue)

        val actualHeapValue =
            resultStores.filter { it.lhs == heapVar }.also { assertEquals(1, it.size) }[0].rhs

        val expectedHeapValue = FunctionApplication(
            STORE,
            FunctionApplication(
                STORE, heapVar, arrVar, FunctionApplication(ARRAY_FIELD, IntValue(1)),
                FunctionApplication(
                    valueOfSym,
                    AdditionExpr(
                        FunctionApplication(intValueSym, makeSelectTerm(1)),
                        IntValue(1)
                    )
                )
            ), arrVar, FunctionApplication(ARRAY_FIELD, IntValue(0)),
            FunctionApplication(
                valueOfSym,
                AdditionExpr(
                    FunctionApplication(intValueSym, makeSelectTerm(1)),
                    FunctionApplication(intValueSym, makeSelectTerm(2))
                )
            )
        )

        assertEquals(expectedHeapValue, actualHeapValue)
    }
}