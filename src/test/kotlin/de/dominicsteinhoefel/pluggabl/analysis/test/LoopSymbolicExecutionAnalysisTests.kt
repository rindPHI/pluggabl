package de.dominicsteinhoefel.pluggabl.analysis.test

import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.pluggabl.analysis.test.SymbolicExecutionTestHelper.compareLeaveInputs
import de.dominicsteinhoefel.pluggabl.analysis.test.SymbolicExecutionTestHelper.compareLeaves
import de.dominicsteinhoefel.pluggabl.analysis.test.SymbolicExecutionTestHelper.compareLoopLeaves
import de.dominicsteinhoefel.pluggabl.analysis.test.SymbolicExecutionTestHelper.compareResultForVariable
import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory
import de.dominicsteinhoefel.pluggabl.theories.IntTheory
import de.dominicsteinhoefel.pluggabl.theories.IntTheory.mult
import de.dominicsteinhoefel.pluggabl.theories.IntTheory.plus
import org.junit.Assert.assertEquals
import org.junit.Test
import soot.jimple.internal.JIfStmt

class LoopSymbolicExecutionAnalysisTests {
    private val SOOT_CLASS_PATH = listOf(
        "./build/classes/kotlin/test",
        "./src/test/lib/java-8-openjdk-amd64-rt.jar",
        "./src/test/lib/java-8-openjdk-amd64-jce.jar",
        "./src/test/lib/kotlin-stdlib-1.4.21.jar",
        "./src/test/lib/annotations-20.1.0.jar"
    )

    @Test
    fun testSimpleLoop() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.Loops",
            "int simpleLoop(int)",
            SOOT_CLASS_PATH
        )

        analysis.symbolicallyExecute()

        val sInput = analysis.getLocal("input")
        val sI = analysis.getLocal("i")
        val sResult = analysis.getLocal("result")
        val resultVar = analysis.symbolsManager.getResultVariable()

        val conditional = ValueSummary.create(
            GuardedExpression(
                GreaterEqualConstr(sInput, IntTheory.IntValue(0)),
                sInput
            ),
            GuardedExpression(
                NegatedConstr.create(GreaterEqualConstr(sInput, IntTheory.IntValue(0))),
                mult(sInput, IntTheory.IntValue(-1))
            )
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
                    ParallelStore.create(
                        ElementaryStore(sI, sILoopResult),
                        ElementaryStore(resultVar, sILoopResult)
                    )
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
            "int simpleLoopWithContinueAndBreak(int)",
            SOOT_CLASS_PATH
        )

        analysis.symbolicallyExecute()

        val sInput = analysis.getLocal("input")
        val sI = analysis.getLocal("i")
        val sResult = analysis.getLocal("result")

        val conditional = ValueSummary.create(
            GuardedExpression(
                GreaterEqualConstr(sInput, IntTheory.IntValue(0)),
                sInput
            ),
            GuardedExpression(
                NegatedConstr.create(GreaterEqualConstr(sInput, IntTheory.IntValue(0))),
                mult(sInput, IntTheory.IntValue(-1))
            )
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

        compareLeaveInputs(expectedLeaves, analysis)
        compareLoopLeaves(expectedLoopLeaves, analysis)
    }

    @Test
    fun testReallySimpleLoop() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.Loops",
            "int reallySimpleLoop(int)",
            SOOT_CLASS_PATH
        )

        analysis.symbolicallyExecute()

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

        val leafSES = analysis.getLeavesWithOutputSESs().values.toList().also { assertEquals(2, it.size) }[0]

        assertEquals(listOf(expectedLeafSES), leafSES)

        // Test output states for if stmt
        val ifStmt = analysis.body.units.filterIsInstance<JIfStmt>().toList().also { assertEquals(1, it.size) }[0]

        val expectedIfStmtOutputs = listOf(
            SymbolicExecutionState(
                SymbolicConstraintSet.from(
                    NegatedConstr.create(
                        GreaterEqualConstr(
                            iAnonLoopTerm,
                            input
                        )
                    )
                ),
                ElementaryStore(i, iAnonLoopTerm)
            ),
            SymbolicExecutionState(
                SymbolicConstraintSet.from(GreaterEqualConstr(iAfterLoopTerm, input)),
                ElementaryStore(i, iAfterLoopTerm)
            )
        )

        assertEquals(expectedIfStmtOutputs, analysis.getOutputSESs(ifStmt))
    }

    @Test
    fun testLoopWithNonTrivialGuard() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.Loops",
            "int loopWithNonTrivialGuard(java.lang.Integer[])",
            SOOT_CLASS_PATH
        )

        // TODO: Why is
        //   Node "staticinvoke <kotlin.jvm.internal.Intrinsics: void checkParameterIsNotNull(java.lang.Object,java.lang.String)>(input, "input")":
        // executed without complaints?

        analysis.symbolicallyExecute()

        // loop leaf SES:
        // ({!((i_ANON_LOOP_0(itCnt_LOOP_0, 0))>=(length(input)))}, [$stack3 -> length(input)]++[i -> plusInt(i_ANON_LOOP_0(itCnt_LOOP_0, 0), 1)])

        val input = analysis.getLocal("input")
        val i = analysis.getLocal("i")
        val result = analysis.getLocal("result")
        val stack3 = analysis.getLocal("\$stack3")
        val lengthInput = FunctionApplication(HeapTheory.ARRAY_LENGTH, input)

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
                            lengthInput
                        )
                    )
                ),
                ParallelStore.create(
                    ElementaryStore(stack3, lengthInput),
                    ElementaryStore(i, plus(iAnonLoopTerm, IntTheory.IntValue(1)))
                )
            )
        )

        compareLoopLeaves(expectedLoopLeaves, analysis)

        // result SES:
        // ({(i_AFTER_LOOP_0(iterations_LOOP_0(0, input), 0))>=(length(input))}, [i -> i_AFTER_LOOP_0(iterations_LOOP_0(0, input), 0)]++[$stack3 -> length(input)]++[result -> i_AFTER_LOOP_0(iterations_LOOP_0(0, input), 0)])

        val iAfterLoopTerm = FunctionApplication(
            analysis.getFunctionSymbol("i_AFTER_LOOP_0"),
            FunctionApplication(analysis.getFunctionSymbol("iterations_LOOP_0"), IntTheory.IntValue(0), input),
            IntTheory.IntValue(0),
            input
        )

        val expectedLeafSES =
            SymbolicExecutionState(
                SymbolicConstraintSet.from(GreaterEqualConstr(iAfterLoopTerm, lengthInput)),
                ParallelStore.create(
                    ElementaryStore(i, iAfterLoopTerm),
                    ParallelStore.create(ElementaryStore(stack3, lengthInput), ElementaryStore(result, iAfterLoopTerm))
                )
            )

        val leafSES = analysis.getLeavesWithOutputSESs().values.toList().also { assertEquals(2, it.size) }[0]

        assertEquals(listOf(expectedLeafSES), leafSES)
    }

    @Test
    fun testArrayLoop() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.Loops",
            "int arrayLoop(java.lang.Integer[])",
            SOOT_CLASS_PATH
        )

        analysis.symbolicallyExecute()

        val result = analysis.getLocal("result")
        val input = analysis.getLocal("input")
        val heap = HeapTheory.HEAP_VAR

        val zero = IntTheory.IntValue(0)
        val one = IntTheory.IntValue(1)

        val heapAfterLoopSymbol = analysis.symbolsManager.getFunctionSymbols().first { it.name == "heap_AFTER_LOOP_0" }
        val iterationsLoopSymbol = analysis.symbolsManager.getFunctionSymbols().first { it.name == "iterations_LOOP_0" }

        val intValue = analysis.symbolsManager.getMethodResultSymbol("java.lang.Integer", "int intValue()")!!
        val integerSelectAll = HeapTheory.SelectAll.create(analysis.typeConverter.typeByName("java.lang.Integer")!!)
        val integerSelect = HeapTheory.Select.create(analysis.typeConverter.typeByName("java.lang.Integer")!!)

        val resultSES = analysis.getLeavesWithOutputSESs().values
            .also { assertEquals(2, it.size) }.toList()[0]
            .also { assertEquals(1, it.size) }[0]

        val heapAfterLoop = FunctionApplication(
            heapAfterLoopSymbol,
            FunctionApplication(iterationsLoopSymbol, zero, input),
            input,
            zero,
            FunctionApplication(integerSelectAll, heap, input)
        )

        compareResultForVariable(heapAfterLoop, resultSES, heap)

        val expectedResultExpr = FunctionApplication(
            intValue,
            FunctionApplication(
                integerSelect,
                heapAfterLoop,
                input,
                FunctionApplication(
                    HeapTheory.ARRAY_FIELD,
                    IntTheory.minus(FunctionApplication(HeapTheory.ARRAY_LENGTH, input), one)
                )
            )
        )

        compareResultForVariable(expectedResultExpr, resultSES, result)

        val itCntLOOP0 = analysis.getLocal("itCnt_LOOP_0")
        val valueOf =
            analysis.symbolsManager.getMethodResultSymbol("java.lang.Integer", "java.lang.Integer valueOf(int)")!!

        val heapAnonLoopSymbol = analysis.symbolsManager.getFunctionSymbols().first { it.name == "heap_ANON_LOOP_0" }
        val iAnonLoopSymbol = analysis.symbolsManager.getFunctionSymbols().first { it.name == "i_ANON_LOOP_0" }
        val heapAnonLoopTerm = FunctionApplication(
            heapAnonLoopSymbol,
            itCntLOOP0,
            input,
            zero,
            FunctionApplication(integerSelectAll, heap, input)
        )
        val iAnonTerm = FunctionApplication(iAnonLoopSymbol, itCntLOOP0, zero, input)
        val ithInputElem = FunctionApplication(HeapTheory.ARRAY_FIELD, iAnonTerm)

        val loopHeapExpr = FunctionApplication(
            HeapTheory.STORE,
            heapAnonLoopTerm,
            input,
            ithInputElem,
            FunctionApplication(
                valueOf,
                mult(
                    FunctionApplication(
                        intValue,
                        FunctionApplication(integerSelect, heapAnonLoopTerm, input, ithInputElem)
                    ), iAnonTerm
                )
            )
        )

        val loopLeafSES = analysis.getLoopLeafSESs().values.toList()[0]

        compareResultForVariable(loopHeapExpr, loopLeafSES, heap)
    }

// TODO: Add test case for nested loop, maybe even w/ labeled break
}