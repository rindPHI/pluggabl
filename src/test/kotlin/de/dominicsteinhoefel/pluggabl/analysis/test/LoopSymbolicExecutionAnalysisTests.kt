package de.dominicsteinhoefel.pluggabl.analysis.test

import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.pluggabl.analysis.test.SymbolicExecutionTestHelper.compareLeaves
import de.dominicsteinhoefel.pluggabl.analysis.test.SymbolicExecutionTestHelper.compareLoopLeaves
import de.dominicsteinhoefel.pluggabl.analysis.test.SymbolicExecutionTestHelper.printSESs
import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.theories.IntTheory
import de.dominicsteinhoefel.pluggabl.theories.IntTheory.mult
import de.dominicsteinhoefel.pluggabl.theories.IntTheory.plus
import org.junit.Assert.assertEquals
import org.junit.Test
import soot.jimple.Stmt

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
            GreaterEqualConstr(sInput, IntTheory.IntValue(0)),
            sInput,
            mult(sInput, IntTheory.IntValue(-1))
        )

        val sILoopResult =
            FunctionApplication(
                analysis.getFunctionSymbol("i_AFTER_LOOP_0"),
                listOf(
                    FunctionApplication(
                        analysis.getFunctionSymbol("iterations_LOOP_0"),
                        listOf(IntTheory.IntValue(0), conditional)
                    ),
                    IntTheory.IntValue(0),
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
                analysis.getFunctionSymbol("i_ANON_LOOP_0"),
                listOf(
                    analysis.getLocal("itCnt_LOOP_0"),
                    IntTheory.IntValue(0),
                    conditional
                )
            )

        val expectedLoopLeaves = listOf(
            SymbolicExecutionState(
                SymbolicConstraintSet.from(NegatedConstr.create(GreaterEqualConstr(sIAnonLoop, conditional))),
                ParallelStore.create(
                    ElementaryStore(sResult, conditional),
                    ElementaryStore(sI, plus(sIAnonLoop, IntTheory.IntValue(1)))
                )
            )
        )

        compareLeaves(expectedLeaves, analysis)
        compareLoopLeaves(expectedLoopLeaves, analysis)
    }

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
            GreaterEqualConstr(sInput, IntTheory.IntValue(0)),
            sInput,
            mult(sInput, IntTheory.IntValue(-1))
        )

        val sILoopResult =
            FunctionApplication(
                analysis.getFunctionSymbol("i_AFTER_LOOP_0"),
                listOf(
                    FunctionApplication(
                        analysis.getFunctionSymbol("iterations_LOOP_0"),
                        listOf(IntTheory.IntValue(0), conditional)
                    ),
                    IntTheory.IntValue(0),
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
                    NegatedConstr.create(EqualityConstr.create(sILoopResult, IntTheory.IntValue(17))),
                    EqualityConstr.create(sILoopResult, IntTheory.IntValue(42))
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
                    IntTheory.IntValue(0),
                    conditional
                )
            )

        val expectedLoopLeaves = listOf(
            SymbolicExecutionState(
                SymbolicConstraintSet.from(
                    NegatedConstr.create(GreaterEqualConstr(sIAnonLoop, conditional)),
                    Or.create(
                        EqualityConstr.create(sIAnonLoop, IntTheory.IntValue(17)),
                        NegatedConstr.create(EqualityConstr.create(sIAnonLoop, IntTheory.IntValue(42)))
                    )
                ),
                ParallelStore.create(
                    ElementaryStore(sResult, conditional),
                    ElementaryStore(sI, plus(sIAnonLoop, IntTheory.IntValue(1)))
                )
            )
        )

        compareLeaves(expectedLeaves, analysis)
        compareLoopLeaves(expectedLoopLeaves, analysis)
    }

    @Test
    fun testReallySimpleLoop() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.Loops",
            "int reallySimpleLoop(int)"
        )

        analysis.symbolicallyExecute()

        printSESs(analysis)

        // loop leaf SES:
        // ({!((i_ANON_LOOP_0(itCnt_LOOP_0, 0, input))>=(input))}, [i -> plusInt(i_ANON_LOOP_0(itCnt_LOOP_0, 0, input), 1)])

        val input = analysis.getLocal("input")
        val i = analysis.getLocal("i")
        val result = analysis.getLocal("result")

        val iAnonLoopTerm = FunctionApplication(
            analysis.getFunctionSymbol("i_ANON_LOOP_0"),
            analysis.getLocal("itCnt_LOOP_0"),
            IntTheory.IntValue(0),
            input
        )

        val expectedLoopLeaves = listOf(
            SymbolicExecutionState(
                SymbolicConstraintSet.from(
                    NegatedConstr.create(
                        GreaterEqualConstr(
                            iAnonLoopTerm,
                            input
                        )
                    )
                ),
                ElementaryStore(i, IntTheory.plus(iAnonLoopTerm, IntTheory.IntValue(1)))
            )
        )

        compareLoopLeaves(expectedLoopLeaves, analysis)

        // leaf SES:
        // ({(i_AFTER_LOOP_0(iterations_LOOP_0(0, input), 0, input))>=(input)}, [i -> i_AFTER_LOOP_0(iterations_LOOP_0(0, input), 0, input)]++[result -> i_AFTER_LOOP_0(iterations_LOOP_0(0, input), 0, input)])

        val iAfterLoopTerm = FunctionApplication(
            analysis.getFunctionSymbol("i_AFTER_LOOP_0"),
            FunctionApplication(analysis.getFunctionSymbol("iterations_LOOP_0"), IntTheory.IntValue(0), input),
            IntTheory.IntValue(0),
            input
        )

        val expectedLeafSES =
            SymbolicExecutionState(
                SymbolicConstraintSet.from(GreaterEqualConstr(iAfterLoopTerm, input)),
                ParallelStore.create(ElementaryStore(i, iAfterLoopTerm), ElementaryStore(result, iAfterLoopTerm))
            )

        val leafSES = analysis.getOutputSESs(analysis.cfg.tails.also { assertEquals(1, it.size) }[0] as Stmt)
            .also { assertEquals(1, it.size) }[0]

        assertEquals(expectedLeafSES, leafSES)
    }

    @Test
    fun testLoopWithNonTrivialGuard() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.Loops",
            "int loopWithNonTrivialGuard(java.lang.Integer[])"
        )

        // TODO: Why is
        //   Node "staticinvoke <kotlin.jvm.internal.Intrinsics: void checkParameterIsNotNull(java.lang.Object,java.lang.String)>(input, "input")":
        // executed without complaints?

        analysis.symbolicallyExecute()

        printSESs(analysis)

        TODO("Implement Tests")
    }

    // TODO: Add test case for nested loop, maybe even w/ labeled break
}