package de.dominicsteinhoefel.symbex

import de.dominicsteinhoefel.symbex.SymbolicExecutionTestHelper.compareLeaves
import de.dominicsteinhoefel.symbex.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.symbex.expr.*
import junit.framework.Assert.fail
import org.junit.Assert
import org.junit.Test

class SimpleSymbolicExecutionAnalysisTests {
    @Test
    fun testSimpleTwoBranchedMethod() {
        val sInput = LocalVariable("input", INT_TYPE)
        val sTest = LocalVariable("test", INT_TYPE)
        val sStack = LocalVariable("\$stack3", INT_TYPE)
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

        val analysis = SymbolicExecutionAnalysis(
            "de.dominicsteinhoefel.symbex.SimpleMethods",
            "int simpleTwoBranchedMethod(int)"
        )

        analysis.symbolicallyExecute()
        compareLeaves(expected, analysis)
    }

    @Test
    fun testSimpleTwoBranchedMethodWithMerge() {
        val sInput = LocalVariable("input", INT_TYPE)
        val sTest = LocalVariable("test", INT_TYPE)
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

        val analysis = SymbolicExecutionAnalysis(
            "de.dominicsteinhoefel.symbex.SimpleMethods",
            "int simpleTwoBranchedMethodWithMerge(int)"
        )

        analysis.symbolicallyExecute()
        compareLeaves(expected, analysis)
    }

    @Test
    fun testSimpleTwoBranchedMethodWithMergeFieldAccess() {
        val sInput = LocalVariable("input", INT_TYPE)
        val sTest = LocalVariable("test", INT_TYPE)
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

        val analysis = SymbolicExecutionAnalysis(
            "de.dominicsteinhoefel.symbex.SimpleMethods",
            "void simpleTwoBranchedMethodWithMergeFieldAccess()"
        )

        analysis.symbolicallyExecute()
        compareLeaves(expected, analysis)
    }

    @Test
    fun testSimpleLoop() {
        val sInput = LocalVariable("input", INT_TYPE)
        val sI = LocalVariable("i", INT_TYPE)
        val sIAnonLoop = LocalVariable("i_ANON_LOOP", INT_TYPE)
        val sResult = LocalVariable("result", INT_TYPE)
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

        fail("Loop analysis not yet implemented.")

//        symbolicallyExecuteMethod(
//            "de.dominicsteinhoefel.symbex.SimpleMethods",
//            "int simpleLoop(int)",
//            compareLeaves(expected)
//        )
    }
}