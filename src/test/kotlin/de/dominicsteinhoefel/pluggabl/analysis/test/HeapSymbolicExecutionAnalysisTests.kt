package de.dominicsteinhoefel.pluggabl.analysis.test

import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.simplification.SymbolicExpressionSimplifier
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory.ARRAY_FIELD
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory.HEAP_VAR
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory.STORE
import de.dominicsteinhoefel.pluggabl.theories.IntTheory
import de.dominicsteinhoefel.pluggabl.theories.IntTheory.INT_TYPE
import de.dominicsteinhoefel.pluggabl.theories.IntTheory.minus
import de.dominicsteinhoefel.pluggabl.theories.IntTheory.plus
import org.junit.Assert.assertEquals
import org.junit.Test
import soot.jimple.Stmt

class HeapSymbolicExecutionAnalysisTests {
    private val SOOT_CLASS_PATH = listOf(
        "./build/classes/kotlin/test",
        "./src/test/lib/java-8-openjdk-amd64-rt.jar",
        "./src/test/lib/java-8-openjdk-amd64-jce.jar",
        "./src/test/lib/kotlin-stdlib-1.4.21.jar"
    )

    @Test
    fun testSimpleTwoBranchedMethodWithMergeFieldAccess() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.HeapAccess",
            "void simpleTwoBranchedMethodWithMergeFieldAccess()",
            SOOT_CLASS_PATH
        )

        analysis.symbolicallyExecute()

        assertEquals(1, analysis.cfg.tails.size)

        val leafState = analysis.getInputSESs(analysis.cfg.tails[0] as Stmt)[0].also {
            assertEquals(
                true,
                it.constraints.isEmpty()
            )
        }

        val thisVar = analysis.getLocal("this")

        val selectTerm = FunctionApplication(
            HeapTheory.Select.create(INT_TYPE),
            HEAP_VAR,
            thisVar,
            analysis.symbolsManager.getFieldSymbol("de.dominicsteinhoefel.pluggabl.testcase.HeapAccess", "test")!!
        )

        val evaluatedExpression =
            SymbolicExpressionSimplifier.simplify(StoreApplExpression.create(leafState.store, selectTerm))

        // Expression should contain a conditional term with constraint "this.input == 42". We consider both cases.
        val selectInput = FunctionApplication(
            HeapTheory.Select.create(INT_TYPE),
            HEAP_VAR,
            thisVar,
            analysis.symbolsManager.getFieldSymbol("de.dominicsteinhoefel.pluggabl.testcase.HeapAccess", "input")!!
        )

        val condition = EqualityConstr.create(selectInput, IntTheory.IntValue(42))

        fun replaceConditionWith(newCondition: SymbolicConstraint) =
            fun(e: SymbolicExpression) =
                if (e is ValueSummary)
                    ValueSummary.create(e.expressions.map {
                        when (it.condition) {
                            condition -> GuardedExpression(newCondition, it.value)
                            NegatedConstr.create(condition) ->
                                GuardedExpression(NegatedConstr.create(newCondition), it.value)
                            else -> it
                        }
                    })
                else e

        val expr1 = SymbolicExpressionSimplifier.simplify(
            evaluatedExpression.accept(ExpressionReplacer(replaceConditionWith(True)))
        )

        val expr2 = SymbolicExpressionSimplifier.simplify(
            evaluatedExpression.accept(ExpressionReplacer(replaceConditionWith(False)))
        )

        assertEquals(plus(plus(selectInput, IntTheory.IntValue(2)), IntTheory.IntValue(4)), expr1)
        assertEquals(plus(plus(selectInput, IntTheory.IntValue(3)), IntTheory.IntValue(4)), expr2)
    }

    @Test
    fun testSimpleArrayAccess() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.HeapAccess",
            "int simpleArrayAccess(java.lang.Integer[])",
            SOOT_CLASS_PATH
        )

        analysis.symbolicallyExecute()

        val intValueSym = analysis.symbolsManager.getMethodResultSymbol(
            "java.lang.Integer", "int intValue()"
        )!!

        val valueOfSym = analysis.symbolsManager.getMethodResultSymbol(
            "java.lang.Integer", "java.lang.Integer valueOf(int)"
        )!!

        val arrVar = analysis.getLocal("arr")
        val heapVar = analysis.getLocal("heap")
        val resultVar = analysis.getLocal("result")
        val integerType = ReferenceType("java.lang.Integer", OBJECT_TYPE)

        fun makeSelectTerm(v: Int) = FunctionApplication(
            HeapTheory.Select.create(integerType), heapVar, arrVar, FunctionApplication(
                ARRAY_FIELD,
                IntTheory.IntValue(v)
            )
        )

        val expectedResultValue = plus(
            FunctionApplication(
                intValueSym,
                FunctionApplication(
                    valueOfSym,
                    minus(
                        FunctionApplication(intValueSym, makeSelectTerm(1)),
                        FunctionApplication(intValueSym, makeSelectTerm(2))
                    )
                )
            ), IntTheory.IntValue(1)
        )

        val resultStores = SymbolicStore.elementaries(
            analysis.getOutputSESs(
                analysis.cfg.tails.also { assertEquals(1, it.size) }[0] as Stmt
            ).also { assertEquals(1, it.size) }[0].store
        )

        val actualResultValue =
            resultStores.filter { it.lhs == resultVar }.also { assertEquals(1, it.size) }[0].rhs

        assertEquals(expectedResultValue, actualResultValue)

        val actualHeapValue =
            resultStores.filter { it.lhs == heapVar }.also { assertEquals(1, it.size) }[0].rhs

        val expectedHeapValue = FunctionApplication(
            STORE,
            FunctionApplication(
                STORE, heapVar, arrVar, FunctionApplication(ARRAY_FIELD, IntTheory.IntValue(1)),
                FunctionApplication(
                    valueOfSym,
                    plus(
                        FunctionApplication(intValueSym, makeSelectTerm(1)),
                        IntTheory.IntValue(1)
                    )
                )
            ), arrVar, FunctionApplication(ARRAY_FIELD, IntTheory.IntValue(0)),
            FunctionApplication(
                valueOfSym,
                minus(
                    FunctionApplication(intValueSym, makeSelectTerm(1)),
                    FunctionApplication(intValueSym, makeSelectTerm(2))
                )
            )
        )

        assertEquals(expectedHeapValue, actualHeapValue)
    }
}