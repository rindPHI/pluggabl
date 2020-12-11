package de.dominicsteinhoefel.pluggabl.test

import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.test.SymbolicExecutionTestHelper.compareLeaves
import de.dominicsteinhoefel.pluggabl.test.SymbolicExecutionTestHelper.compareLoopLeaves
import org.junit.Test
import soot.jimple.internal.JimpleLocal

class LoopSymbolicExecutionAnalysisTests {

    @Test
    fun testSimpleLoop() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.Loops",
            "int simpleLoop(int)"
        )

        analysis.symbolicallyExecute()

        val sInput = analysis.getLocal("input")
        val sI = analysis.getLocal("i")
        val sResult = analysis.getLocal("result")

        val conditional = ConditionalExpression.create(
            GreaterEqualConstr(sInput, IntValue(0)),
            sInput,
            MultiplicationExpr(sInput, IntValue(-1))
        )

        val sILoopResult =
            FunctionApplication(
                analysis.getFunctionSymbol("i_AFTER_LOOP_0"),
                listOf(
                    FunctionApplication(
                        analysis.getFunctionSymbol("iterations_LOOP_0"),
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
                analysis.getFunctionSymbol( "i_ANON_LOOP_0"),
                listOf(
                    analysis.getLocal("itCnt_LOOP_0"),
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

        compareLeaves(expectedLeaves, analysis)
        compareLoopLeaves(expectedLoopLeaves, analysis)
    }

    // TODO: Add test case for nested loop, maybe even w/ labeled break

    @Test
    fun testSimpleLoopWithContinueAndBreak() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.Loops",
            "int simpleLoopWithContinueAndBreak(int)"
        )

        analysis.symbolicallyExecute()

        val sInput = analysis.getLocal("input")
        val sI = analysis.getLocal("i")
        val sResult = analysis.getLocal("result")

        val conditional = ConditionalExpression.create(
            GreaterEqualConstr(sInput, IntValue(0)),
            sInput,
            MultiplicationExpr(sInput, IntValue(-1))
        )

        val sILoopResult =
            FunctionApplication(
                analysis.getFunctionSymbol("i_AFTER_LOOP_0"),
                listOf(
                    FunctionApplication(
                        analysis.getFunctionSymbol("iterations_LOOP_0"),
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
                analysis.getFunctionSymbol("i_ANON_LOOP_0"),
                listOf(
                    analysis.getLocal("itCnt_LOOP_0"),
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

        compareLeaves(expectedLeaves, analysis)
        compareLoopLeaves(expectedLoopLeaves, analysis)
    }
}