package de.dominicsteinhoefel.pluggabl.test

import de.dominicsteinhoefel.pluggabl.test.SymbolicExecutionTestHelper.compareLeaves
import de.dominicsteinhoefel.pluggabl.test.SymbolicExecutionTestHelper.compareLoopLeaves
import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.simplification.SymbolicExpressionSimplifier
import de.dominicsteinhoefel.pluggabl.test.SymbolicExecutionTestHelper.printLeafSESs
import de.dominicsteinhoefel.pluggabl.test.SymbolicExecutionTestHelper.printSESs
import de.dominicsteinhoefel.pluggabl.theories.FIELD_TYPE
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

        val selectTerm = FunctionApplication(
            Select(INT_TYPE),
            HEAP_VAR,
            analysis.localVariables.first { it.name == "this" },
            FunctionApplication(FunctionSymbol("<de.dominicsteinhoefel.pluggabl.testcase.HeapAccess: int test>", FIELD_TYPE))
        )

        val thisVar = analysis.localVariables.first { it.name == "this" }

        val evaluatedExpression =
            SymbolicExpressionSimplifier.simplify(StoreApplExpression.create(leafState.store, selectTerm))

        // Expression should contain a conditional term with constraint "this.input == 42". We consider both cases.
        val selectInput = FunctionApplication(
            Select(INT_TYPE),
            HEAP_VAR,
            thisVar,
            FunctionApplication(
                FunctionSymbol(
                    "<de.dominicsteinhoefel.pluggabl.testcase.HeapAccess: int input>",
                    FIELD_TYPE
                )
            )
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

    //@Test
    fun testSimpleArrayAccess() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.HeapAccess",
            "int simpleArrayAccess(java.lang.Integer[])"
        )

        analysis.symbolicallyExecute()
        printSESs(analysis)
    }
}