package de.dominicsteinhoefel.pluggabl.analysis.test

import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.pluggabl.analysis.test.SymbolicExecutionTestHelper.compareLeaves
import de.dominicsteinhoefel.pluggabl.analysis.test.SymbolicExecutionTestHelper.printSESs
import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory
import de.dominicsteinhoefel.pluggabl.theories.IntTheory.IntValue
import de.dominicsteinhoefel.pluggabl.theories.IntTheory.plus
import org.junit.Test

class ParenthesisParserTest {

    @Test
    fun testParenthesisParserAnalysis() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.ParenthesisParser",
            "int parse(java.lang.Character[])"
        )

        analysis.symbolicallyExecute()

        val input = analysis.getLocal("input")
        val i = analysis.getLocal("i")
        val c = analysis.getLocal("c")
        val stack5 = analysis.getLocal("\$stack5")
        val stack6 = analysis.getLocal("\$stack6")
        val opParCnt = analysis.getLocal("opParCnt")
        val result = analysis.symbolsManager.getResultVariable()

        val zero = IntValue(0)
        val lPar = IntValue('('.toInt())
        val rPar = IntValue(')'.toInt())

        val itCnt = FunctionApplication(analysis.getFunctionSymbol("iterations_LOOP_0"), zero, input, zero)
        val iAfterLoop = FunctionApplication(analysis.getFunctionSymbol("i_AFTER_LOOP_0"), itCnt, zero, input, zero)
        val opParCntAfterLoop =
            FunctionApplication(analysis.getFunctionSymbol("opParCnt_AFTER_LOOP_0"), itCnt, zero, input, zero)
        val cAfterLoop = FunctionApplication(analysis.getFunctionSymbol("c_AFTER_LOOP_0"), itCnt, input, zero, zero)
        val stack6AfterLoop = FunctionApplication(analysis.getFunctionSymbol("\$stack6_AFTER_LOOP_0"), itCnt, input, zero, zero)

        val lengthOfInput = FunctionApplication(HeapTheory.ARRAY_LENGTH, input)

        val charValue = analysis.symbolsManager.getMethodResultSymbol("java.lang.Character", "char charValue()")!!

        val selectIthInput = FunctionApplication(
            HeapTheory.Select.create(analysis.typeConverter.typeByName("java.lang.Character")!!),
            HeapTheory.HEAP_VAR,
            input,
            FunctionApplication(HeapTheory.ARRAY_FIELD, iAfterLoop)
        )

        val charValueOfIthInput = FunctionApplication(charValue, selectIthInput)

        val firstErrorResultSES = SymbolicExecutionState(
            SymbolicConstraintSet.from(
                NegatedConstr.create(GreaterEqualConstr(iAfterLoop, lengthOfInput)),
                NegatedConstr.create(EqualityConstr.create(charValueOfIthInput, lPar)),
                EqualityConstr.create(charValueOfIthInput, rPar),
                NegatedConstr.create(
                    GreaterEqualConstr(
                        plus(opParCntAfterLoop, IntValue(-1)),
                        zero
                    )
                )
            ),
            ParallelStore.create(
                ElementaryStore(i, iAfterLoop),
                ElementaryStore(stack5, lengthOfInput),
                ElementaryStore(stack6, selectIthInput),
                ElementaryStore(c, charValueOfIthInput),
                ElementaryStore(opParCnt, plus(opParCntAfterLoop, IntValue(-1))),
                ElementaryStore(result, IntValue(1))
            )
        )

        val secondErrorResultSES = SymbolicExecutionState(
            SymbolicConstraintSet.from(
                NegatedConstr.create(GreaterEqualConstr(iAfterLoop, lengthOfInput)),
                NegatedConstr.create(EqualityConstr.create(charValueOfIthInput, lPar)),
                NegatedConstr.create(EqualityConstr.create(charValueOfIthInput, rPar))
            ),
            ParallelStore.create(
                ElementaryStore(opParCnt, opParCntAfterLoop),
                ElementaryStore(i, iAfterLoop),
                ElementaryStore(stack5, lengthOfInput),
                ElementaryStore(stack6, selectIthInput),
                ElementaryStore(c, charValueOfIthInput),
                ElementaryStore(result, IntValue(1))
            )
        )

        val correctResultSES = SymbolicExecutionState(
            SymbolicConstraintSet.from(
                GreaterEqualConstr(iAfterLoop, lengthOfInput),
                EqualityConstr.create(opParCntAfterLoop, zero)
            ),
            ParallelStore.create(
                ElementaryStore(opParCnt, opParCntAfterLoop),
                ElementaryStore(stack6, stack6AfterLoop),
                ElementaryStore(c, cAfterLoop),
                ElementaryStore(i, iAfterLoop),
                ElementaryStore(stack5, lengthOfInput),
                ElementaryStore(result, IntValue(0))
            )
        )

        val thirdErrorResultSES = SymbolicExecutionState(
            SymbolicConstraintSet.from(
                GreaterEqualConstr(iAfterLoop, lengthOfInput),
                NegatedConstr.create(EqualityConstr.create(opParCntAfterLoop, zero))
            ),
            ParallelStore.create(
                ElementaryStore(opParCnt, opParCntAfterLoop),
                ElementaryStore(stack6, stack6AfterLoop),
                ElementaryStore(c, cAfterLoop),
                ElementaryStore(i, iAfterLoop),
                ElementaryStore(stack5, lengthOfInput),
                ElementaryStore(result, IntValue(1))
            )
        )

        printSESs(analysis)

        compareLeaves(
            listOf(firstErrorResultSES, secondErrorResultSES, correctResultSES, thirdErrorResultSES),
            analysis
        )
    }

}