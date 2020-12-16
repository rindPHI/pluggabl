package de.dominicsteinhoefel.pluggabl.simplification.test

import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.simplification.SymbolicStoreSimplifier
import de.dominicsteinhoefel.pluggabl.theories.IntTheory
import org.junit.Assert.assertEquals
import org.junit.Test

class TestSESSimplification {

    @Test
    fun testStoreSimplification() {
        // input:
        //(
        //  [i -> 0]++
        //  {[i -> 0]}([i -> f(i, input)]++{[i -> f(i, input)]}([i -> plusInt(i, 1)]))
        //)

        val i = LocalVariable("i", IntTheory.INT_TYPE)
        val input = LocalVariable("input", IntTheory.INT_TYPE)
        val f = FunctionSymbol("f", IntTheory.INT_TYPE, IntTheory.INT_TYPE, IntTheory.INT_TYPE)
        val fTerm = FunctionApplication(f, i, input)

        val updITo0 = ElementaryStore(i, IntTheory.IntValue(0))
        val updIToFTerm = ElementaryStore(i, fTerm)

        val inputStore = ParallelStore.create(
            updITo0, StoreApplStore.create(
                updITo0, ParallelStore.create(
                    updIToFTerm,
                    StoreApplStore.create(updIToFTerm, ElementaryStore(i, IntTheory.plus(i, IntTheory.IntValue(1))))
                )
            )
        )

        // output:
        //(
        //  {(!(f(0, input)>=(input)))},
        //  [i -> plusInt(f(0, input), 1)])
        //)

        val fTerm0 = FunctionApplication(f, IntTheory.IntValue(0), input)

        val outputStore = ElementaryStore(i, IntTheory.plus(fTerm0, IntTheory.IntValue(1)))

        assertEquals(outputStore, SymbolicStoreSimplifier.simplify(inputStore))
    }

    @Test
    fun testSESSimplification() {
        // input:
        //(
        //  {{[i -> 0]}({[i -> f(i, input)]}(!(i>=(input))))},
        //  [i -> 0]++
        //  {[i -> 0]}([i -> f(i, input)]++{[i -> f(i, input)]}([i -> plusInt(i, 1)]))
        //)

        val i = LocalVariable("i", IntTheory.INT_TYPE)
        val input = LocalVariable("input", IntTheory.INT_TYPE)
        val f = FunctionSymbol("f", IntTheory.INT_TYPE, IntTheory.INT_TYPE, IntTheory.INT_TYPE)
        val fTerm = FunctionApplication(f, i, input)

        val updITo0 = ElementaryStore(i, IntTheory.IntValue(0))
        val updIToFTerm = ElementaryStore(i, fTerm)

        val constraint = StoreApplConstraint.create(
            updITo0,
            StoreApplConstraint.create(updIToFTerm, NegatedConstr.create(GreaterEqualConstr(i, input)))
        )

        val store = ParallelStore.create(
            updITo0, StoreApplStore.create(
                updITo0, ParallelStore.create(
                    updIToFTerm,
                    StoreApplStore.create(updIToFTerm, ElementaryStore(i, IntTheory.plus(i, IntTheory.IntValue(1))))
                )
            )
        )

        val inputSES = SymbolicExecutionState(SymbolicConstraintSet().add(constraint), store)

        // output:
        //(
        //  {(!(f(0, input)>=(input)))},
        //  [i -> plusInt(f(0, input), 1)])
        //)

        val fTerm0 = FunctionApplication(f, IntTheory.IntValue(0), input)

        val outputSES = SymbolicExecutionState(
            SymbolicConstraintSet().add(NegatedConstr.create(GreaterEqualConstr(fTerm0, input))),
            ElementaryStore(i, IntTheory.plus(fTerm0, IntTheory.IntValue(1)))
        )

        assertEquals(outputSES, inputSES.simplify())
    }

}