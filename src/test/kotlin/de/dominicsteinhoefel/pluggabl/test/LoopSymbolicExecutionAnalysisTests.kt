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

class LoopSymbolicExecutionAnalysisTests {

    @Test
    fun testSimpleLoop() {
        val sInput = LocalVariable("input", INT_TYPE)
        val sI = LocalVariable("i", INT_TYPE)
        val sResult = LocalVariable("result_0", INT_TYPE)
        val conditional = ConditionalExpression.create(
            GreaterEqualConstr(sInput, IntValue(0)),
            sInput,
            MultiplicationExpr(sInput, IntValue(-1))
        )

        val sILoopResult =
            FunctionApplication(
                FunctionSymbol("i_AFTER_LOOP_0", INT_TYPE, listOf(INT_TYPE, INT_TYPE, INT_TYPE)),
                listOf(
                    FunctionApplication(
                        FunctionSymbol("iterations_LOOP_0", INT_TYPE, listOf(INT_TYPE, INT_TYPE)),
                        listOf(IntValue(0), conditional)
                    ),
                    IntValue(0),
                    conditional
                )
            )

        val expectedLeaves = listOf(
            SymbolicExecutionState(
                SymbolicConstraintSet.from(GreaterEqualConstr(sILoopResult, conditional)),
                ParallelStore.create(
                    ElementaryStore(sResult, conditional),
                    ElementaryStore(sI, sILoopResult)
                )
            )
        )

        val sIAnonLoop =
            FunctionApplication(
                FunctionSymbol("i_ANON_LOOP_0", INT_TYPE, listOf(INT_TYPE, INT_TYPE, INT_TYPE)),
                listOf(
                    LocalVariable("itCnt_LOOP_0", INT_TYPE),
                    IntValue(0),
                    conditional
                )
            )

        val expectedLoopLeaves = listOf(
            SymbolicExecutionState(
                SymbolicConstraintSet.from(NegatedConstr.create(GreaterEqualConstr(sIAnonLoop, conditional))),
                ParallelStore.create(
                    ElementaryStore(sResult, conditional),
                    ElementaryStore(sI, AdditionExpr(sIAnonLoop, IntValue(1)))
                )
            )
        )

        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.Loops",
            "int simpleLoop(int)"
        )

        analysis.symbolicallyExecute()
        compareLeaves(expectedLeaves, analysis)
        compareLoopLeaves(expectedLoopLeaves, analysis)
    }

    // TODO: Add test case for nested loop, maybe even w/ labeled break

    @Test
    fun testSimpleLoopWithContinueAndBreak() {
        val sInput = LocalVariable("input", INT_TYPE)
        val sI = LocalVariable("i", INT_TYPE)
        val sResult = LocalVariable("result_0", INT_TYPE)
        val conditional = ConditionalExpression.create(
            GreaterEqualConstr(sInput, IntValue(0)),
            sInput,
            MultiplicationExpr(sInput, IntValue(-1))
        )

        val sILoopResult =
            FunctionApplication(
                FunctionSymbol("i_AFTER_LOOP_0", INT_TYPE, listOf(INT_TYPE, INT_TYPE, INT_TYPE)),
                listOf(
                    FunctionApplication(
                        FunctionSymbol("iterations_LOOP_0", INT_TYPE, listOf(INT_TYPE, INT_TYPE)),
                        listOf(IntValue(0), conditional)
                    ),
                    IntValue(0),
                    conditional
                )
            )

        val expectedLeaves = listOf(
            SymbolicExecutionState(
                SymbolicConstraintSet.from(GreaterEqualConstr(sILoopResult, conditional)),
                ParallelStore.create(
                    ElementaryStore(sResult, conditional),
                    ElementaryStore(sI, sILoopResult)
                )
            ),
            SymbolicExecutionState(
                SymbolicConstraintSet.from(
                    NegatedConstr.create(GreaterEqualConstr(sILoopResult, conditional)),
                    NegatedConstr.create(EqualityConstr.create(sILoopResult, IntValue(17))),
                    EqualityConstr.create(sILoopResult, IntValue(42))
                ),
                ParallelStore.create(
                    ElementaryStore(sResult, conditional),
                    ElementaryStore(sI, sILoopResult)
                )
            )
        )

        val sIAnonLoop =
            FunctionApplication(
                FunctionSymbol("i_ANON_LOOP_0", INT_TYPE, listOf(INT_TYPE, INT_TYPE, INT_TYPE)),
                listOf(
                    LocalVariable("itCnt_LOOP_0", INT_TYPE),
                    IntValue(0),
                    conditional
                )
            )

        val expectedLoopLeaves = listOf(
            SymbolicExecutionState(
                SymbolicConstraintSet.from(
                    NegatedConstr.create(GreaterEqualConstr(sIAnonLoop, conditional)),
                    Or.create(
                        EqualityConstr.create(sIAnonLoop, IntValue(17)),
                        NegatedConstr.create(EqualityConstr.create(sIAnonLoop, IntValue(42)))
                    )
                ),
                ParallelStore.create(
                    ElementaryStore(sResult, conditional),
                    ElementaryStore(sI, AdditionExpr(sIAnonLoop, IntValue(1)))
                )
            )
        )

        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.Loops",
            "int simpleLoopWithContinueAndBreak(int)"
        )

        analysis.symbolicallyExecute()
        compareLeaves(expectedLeaves, analysis)
        compareLoopLeaves(expectedLoopLeaves, analysis)
    }
}