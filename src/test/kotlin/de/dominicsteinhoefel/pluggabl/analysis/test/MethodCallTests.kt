package de.dominicsteinhoefel.pluggabl.analysis.test

import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.pluggabl.expr.FunctionApplication
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory
import de.dominicsteinhoefel.pluggabl.theories.IntTheory
import org.junit.Assert
import org.junit.Test

class MethodCallTests {
    private val SOOT_CLASS_PATH = listOf(
        "./build/classes/kotlin/test",
        "./src/test/lib/java-8-openjdk-amd64-rt.jar",
        "./src/test/lib/java-8-openjdk-amd64-jce.jar",
        "./src/test/lib/kotlin-stdlib-1.4.21.jar"
    )

    @Test
    fun testCallToUnknownIFMethod() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.testcase.MethodCall",
            "int main(int)",
            SOOT_CLASS_PATH
        )

        analysis.symbolicallyExecute()

        val result = analysis.getLocal("result")
        val input = analysis.getLocal("input")
        val thisVar = analysis.getLocal("this")
        val heap = HeapTheory.HEAP_VAR

        val one = IntTheory.IntValue(1)
        val two = IntTheory.IntValue(2)

        val testResultSymbol = analysis.symbolsManager.getMethodResultSymbol(
            "de.dominicsteinhoefel.pluggabl.testcase.SomeIF",
            "int test(int)"
        )!!

        val testHeapResultSymbol = analysis.symbolsManager.getMethodHeapResultSymbol(
            "de.dominicsteinhoefel.pluggabl.testcase.SomeIF",
            "int test(int)"
        )!!

        val fieldSymbol = analysis.symbolsManager.getFieldSymbol(
            "de.dominicsteinhoefel.pluggabl.testcase.MethodCall",
            "someIF"
        )!!

        val selectFieldValue = FunctionApplication(
            HeapTheory.Select.create(analysis.typeConverter.typeByName("de.dominicsteinhoefel.pluggabl.testcase.SomeIF")!!),
            heap, thisVar, fieldSymbol
        )

        val resultValue =
            IntTheory.plus(FunctionApplication(testResultSymbol, selectFieldValue, IntTheory.mult(input, two)), one)

        val finalHeapValue =
            FunctionApplication(testHeapResultSymbol, selectFieldValue, IntTheory.mult(input, two))

        val resultSES = analysis.getLeavesWithOutputSESs().values
            .also { Assert.assertEquals(1, it.size) }.toList()[0]
            .also { Assert.assertEquals(1, it.size) }[0]

        SymbolicExecutionTestHelper.compareResultForVariable(finalHeapValue, resultSES, heap)
        SymbolicExecutionTestHelper.compareResultForVariable(resultValue, resultSES, result)
    }
}