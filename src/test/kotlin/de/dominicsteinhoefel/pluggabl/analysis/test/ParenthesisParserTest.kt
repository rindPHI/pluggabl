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
    private val SOOT_CLASS_PATH = listOf(
        "./build/classes/kotlin/test",
        "./src/test/lib/java-8-openjdk-amd64-rt.jar",
        "./src/test/lib/java-8-openjdk-amd64-jce.jar",
        "./src/test/lib/kotlin-stdlib-1.4.21.jar"
    )

    @Test
    fun testParenthesisParserAnalysis() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.ParenthesisParser",
            "int parse(java.lang.Character[])",
            SOOT_CLASS_PATH
        )

        analysis.symbolicallyExecute()

        val heap = HeapTheory.HEAP_VAR
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

        val javaLangChar = analysis.typeConverter.typeByName("java.lang.Character")!!

        val selectAllInput = FunctionApplication(
            HeapTheory.SelectAll.create(javaLangChar),
            heap,
            input
        )

        val itCnt = FunctionApplication(analysis.getFunctionSymbol("iterations_LOOP_0"), zero, input, zero)
        val iAfterLoop =
            FunctionApplication(analysis.getFunctionSymbol("i_AFTER_LOOP_0"), itCnt, zero, input, selectAllInput, zero)
        val opParCntAfterLoop =
            FunctionApplication(
                analysis.getFunctionSymbol("opParCnt_AFTER_LOOP_0"),
                itCnt,
                zero,
                input,
                zero,
                selectAllInput
            )
        val cAfterLoop =
            FunctionApplication(analysis.getFunctionSymbol("c_AFTER_LOOP_0"), itCnt, input, zero, selectAllInput, zero)
        val stack6AfterLoop =
            FunctionApplication(
                analysis.getFunctionSymbol("\$stack6_AFTER_LOOP_0"),
                itCnt,
                input,
                zero,
                selectAllInput,
                zero
            )

        val lengthOfInput = FunctionApplication(HeapTheory.ARRAY_LENGTH, input)

        val charValue = analysis.symbolsManager.getMethodResultSymbol("java.lang.Character", "char charValue()")!!

        val selectIthInput = FunctionApplication(
            HeapTheory.Select.create(javaLangChar),
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
                ElementaryStore(stack6, stack6AfterLoop),
                ElementaryStore(c, cAfterLoop),
                ElementaryStore(opParCnt, opParCntAfterLoop),
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
                ElementaryStore(stack6, stack6AfterLoop),
                ElementaryStore(c, cAfterLoop),
                ElementaryStore(opParCnt, opParCntAfterLoop),
                ElementaryStore(i, iAfterLoop),
                ElementaryStore(stack5, lengthOfInput),
                ElementaryStore(result, IntValue(1))
            )
        )

        //printSESs(analysis)

        compareLeaves(
            listOf(firstErrorResultSES, secondErrorResultSES, correctResultSES, thirdErrorResultSES),
            analysis
        )
    }

}