package de.dominicsteinhoefel.pluggabl.test

import de.dominicsteinhoefel.pluggabl.test.SymbolicExecutionTestHelper.compareLeaves
import de.dominicsteinhoefel.pluggabl.test.SymbolicExecutionTestHelper.compareLoopLeaves
import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.simplification.SymbolicExpressionSimplifier
import de.dominicsteinhoefel.pluggabl.theories.FIELD_TYPE
import de.dominicsteinhoefel.pluggabl.theories.HEAP_VAR
import de.dominicsteinhoefel.pluggabl.theories.Select
import org.junit.Assert.assertEquals
import org.junit.Test
import soot.jimple.Stmt

class SimpleSymbolicExecutionAnalysisTests {
    @Test
    fun testSimpleTwoBranchedMethod() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.SimpleMethods",
            "int simpleTwoBranchedMethod(int)"
        )

        analysis.symbolicallyExecute()

        val sInput = analysis.getLocal("input")
        val sTest = analysis.getLocal("test")
        val sStack = analysis.getLocal("\$stack3")

        val inputPlusOne = AdditionExpr(sInput, IntValue(1))
        val inputPlusOneIsFortyTwo = EqualityConstr.create(inputPlusOne, IntValue(42))

        val expected = listOf(
            SymbolicExecutionState(
                SymbolicConstraintSet.from(NegatedConstr.create(inputPlusOneIsFortyTwo)),
                ParallelStore.create(
                    ElementaryStore(
                        sTest,
                        inputPlusOne
                    ), ElementaryStore(sStack, AdditionExpr(inputPlusOne, IntValue(3)))
                )
            ),
            SymbolicExecutionState(
                SymbolicConstraintSet.from(inputPlusOneIsFortyTwo),
                ElementaryStore(
                    sTest,
                    AdditionExpr(AdditionExpr(inputPlusOne, IntValue(2)), IntValue(4))
                )
            )
        )

        compareLeaves(expected, analysis)
    }

    @Test
    fun testSimpleTwoBranchedMethodWithMerge() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.SimpleMethods",
            "int simpleTwoBranchedMethodWithMerge(int)"
        )

        analysis.symbolicallyExecute()

        val sInput = analysis.getLocal("input")
        val sTest = analysis.getLocal("test")

        val inputPlusOne = AdditionExpr(sInput, IntValue(1))

        val expected = listOf(
            SymbolicExecutionState(
                SymbolicConstraintSet(),
                ElementaryStore(
                    sTest,
                    AdditionExpr(
                        ConditionalExpression.create(
                            EqualityConstr.create(inputPlusOne, IntValue(42)),
                            AdditionExpr(inputPlusOne, IntValue(2)),
                            AdditionExpr(inputPlusOne, IntValue(3))
                        ), IntValue(4)
                    )
                )
            )
        )

        compareLeaves(expected, analysis)
    }
}