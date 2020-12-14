package de.dominicsteinhoefel.pluggabl.test

import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.simplification.SymbolicExpressionSimplifier
import de.dominicsteinhoefel.pluggabl.test.SymbolicExecutionTestHelper.printSESs
import de.dominicsteinhoefel.pluggabl.theories.ARRAY_FIELD
import de.dominicsteinhoefel.pluggabl.theories.HEAP_VAR
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

        val intValueOfSym = analysis.symbolsManager.getMethodResultSymbol(
            "java.lang.Integer", "java.lang.Integer valueOf(int)"
        )!!

        val arrVar = analysis.getLocal("arr")
        val heapVar = analysis.getLocal("heap")
        val resultVar = analysis.getLocal("result")

        val selectArr1 = FunctionApplication(
            Select.create(INT_TYPE), heapVar, arrVar, FunctionApplication(ARRAY_FIELD, IntValue(1))
        )

        val selectArr2 = FunctionApplication(
            Select.create(INT_TYPE), heapVar, arrVar, FunctionApplication(ARRAY_FIELD, IntValue(2))
        )

        val expectedResultExpr = AdditionExpr(
            FunctionApplication(
                intValueSym,
                FunctionApplication(
                    intValueOfSym,
                    AdditionExpr(
                        FunctionApplication(intValueSym, selectArr1),
                        FunctionApplication(intValueSym, selectArr2)
                    )
                )
            ), IntValue(1)
        )

        val actualResultExpr =
            analysis.getOutputSESs(analysis.cfg.tails[0] as Stmt)[0].store.accept(SymbolicStoreCollector { c ->
                if (c is ElementaryStore && c.lhs == resultVar)
                    setOf(c.rhs)
                else emptySet()
            }).toList()[0]

        assertEquals(expectedResultExpr, actualResultExpr)

        TODO("Test outcome.")

        /*
        Result value holds:
          <java.lang.Integer: int intValue()>(
            <java.lang.Integer: java.lang.Integer valueOf(int)>(
              <java.lang.Integer: int intValue()>(select(heap, arr, arr(1))) +
              <java.lang.Integer: int intValue()>(select(heap, arr, arr(2)))))
          +1
         */

        /*
        Heap is:
        heap ->
          store(
            store(
              heap,
              arr,
              arr(1),
              <java.lang.Integer: java.lang.Integer valueOf(int)>((<java.lang.Integer: int intValue()>(select(heap, arr, arr(1))))+(1))),
            arr,
            arr(0),
            <java.lang.Integer: java.lang.Integer valueOf(int)>(<java.lang.Integer: int intValue()>(select(heap, arr, arr(1)))+
              (<java.lang.Integer: int intValue()>(select(heap, arr, arr(2))))))]++
         */
    }
}