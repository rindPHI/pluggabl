package de.dominicsteinhoefel.symbex

import de.dominicsteinhoefel.symbex.SymbolicExecutionTestHelper.compareLeaves
import de.dominicsteinhoefel.symbex.SymbolicExecutionTestHelper.symbolicallyExecuteMethod
import de.dominicsteinhoefel.symbex.expr.*
import org.junit.Test

class SimpleSymbolicExecutionFlowAnalysisTests {
    @Test
    fun testSimpleTwoBranchedMethod() {
        val sInput = Symbol("input", INT_TYPE)
        val sTest = Symbol("test", INT_TYPE)
        val sStack = Symbol("\$stack3", INT_TYPE)
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

        symbolicallyExecuteMethod(
            "de.dominicsteinhoefel.symbex.SimpleMethods",
            "int simpleTwoBranchedMethod(int)",
            compareLeaves(expected)
        )
    }

    @Test
    fun testSimpleTwoBranchedMethodWithMerge() {
        val sInput = Symbol("input", INT_TYPE)
        val sTest = Symbol("test", INT_TYPE)
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

        symbolicallyExecuteMethod(
            "de.dominicsteinhoefel.symbex.SimpleMethods",
            "int simpleTwoBranchedMethodWithMerge(int)",
            compareLeaves(expected)
        )
    }

    @Test
    fun testSimpleLoop() {
        val sInput = Symbol("input", INT_TYPE)
        val sI = Symbol("i", INT_TYPE)
        val sIAnonLoop = Symbol("i_ANON_LOOP", INT_TYPE)
        val sResult = Symbol("result", INT_TYPE)
        val conditional = ConditionalExpression.create(
            GreaterEqualConstr(sInput, IntValue(0)),
            sInput,
            MultiplicationExpr(sInput, IntValue(-1))
        )

        val expected = listOf(
            SymbolicExecutionState(
                SymbolicConstraintSet.from(NegatedConstr.create(GreaterEqualConstr(sIAnonLoop, conditional))),
                ParallelStore.create(
                    ElementaryStore(sResult, conditional),
                    ElementaryStore(sI, AdditionExpr(sIAnonLoop, IntValue(1)))
                )
            ),
            SymbolicExecutionState(
                SymbolicConstraintSet.from(GreaterEqualConstr(sIAnonLoop, conditional)),
                ParallelStore.create(
                    ElementaryStore(sResult, conditional),
                    ElementaryStore(sI, sIAnonLoop)
                )
            )
        )

        symbolicallyExecuteMethod(
            "de.dominicsteinhoefel.symbex.SimpleMethods",
            "int simpleLoop(int)",
            compareLeaves(expected)
        )
    }
}