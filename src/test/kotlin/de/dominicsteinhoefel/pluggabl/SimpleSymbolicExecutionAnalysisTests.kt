package de.dominicsteinhoefel.pluggabl

import de.dominicsteinhoefel.pluggabl.SymbolicExecutionTestHelper.compareLeaves
import de.dominicsteinhoefel.pluggabl.SymbolicExecutionTestHelper.compareLoopLeaves
import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.simplification.SymbolicExpressionSimplifier
import de.dominicsteinhoefel.pluggabl.theories.FIELD_TYPE
import de.dominicsteinhoefel.pluggabl.theories.HEAP_VAR
import de.dominicsteinhoefel.pluggabl.theories.Select
import org.junit.Assert.assertEquals
import org.junit.Test
import soot.jimple.Stmt

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

        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.SimpleMethods",
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

        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.SimpleMethods",
            "int simpleTwoBranchedMethodWithMerge(int)"
        )

        analysis.symbolicallyExecute()
        compareLeaves(expected, analysis)
    }

    @Test
    fun testSimpleTwoBranchedMethodWithMergeFieldAccess() {
        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.SimpleMethods",
            "void simpleTwoBranchedMethodWithMergeFieldAccess()"
        )

        analysis.symbolicallyExecute()

        assertEquals(1, analysis.cfg.tails.size)

        val leafState = analysis.getInputSESs(analysis.cfg.tails[0] as Stmt)[0].also {
            assertEquals(
                true,
                it.constraints.isEmpty()
            )
        }

        val selectTerm = FunctionApplication(
            Select(INT_TYPE),
            HEAP_VAR,
            analysis.localVariables.first { it.name == "this" },
            FunctionApplication(FunctionSymbol("<de.dominicsteinhoefel.pluggabl.SimpleMethods: int test>", FIELD_TYPE))
        )

        val thisVar = analysis.localVariables.first { it.name == "this" }

        val evaluatedExpression =
            SymbolicExpressionSimplifier.simplify(StoreApplExpression.create(leafState.store, selectTerm))

        // Expression should contain a conditional term with constraint "this.input == 42". We consider both cases.
        val selectInput = FunctionApplication(
            Select(INT_TYPE),
            HEAP_VAR,
            thisVar,
            FunctionApplication(
                FunctionSymbol(
                    "<de.dominicsteinhoefel.pluggabl.SimpleMethods: int input>",
                    FIELD_TYPE
                )
            )
        )

        val condition = EqualityConstr.create(selectInput, IntValue(42))

        fun replaceConditionWith(newCondition: SymbolicConstraint) =
            fun(e: SymbolicExpression) =
                if (e is ConditionalExpression && e.condition == condition)
                    ConditionalExpression.create(
                        newCondition,
                        e.vThen,
                        e.vElse
                    )
                else e

        val expr1 = SymbolicExpressionSimplifier.simplify(
            evaluatedExpression.accept(ExpressionReplacer(replaceConditionWith(True)))
        )

        val expr2 = SymbolicExpressionSimplifier.simplify(
            evaluatedExpression.accept(ExpressionReplacer(replaceConditionWith(False)))
        )

        assertEquals(AdditionExpr(AdditionExpr(selectInput, IntValue(2)), IntValue(4)), expr1)
        assertEquals(AdditionExpr(AdditionExpr(selectInput, IntValue(3)), IntValue(4)), expr2)
    }

    @Test
    fun testSimpleLoop() {
        val sInput = LocalVariable("input", INT_TYPE)
        val sI = LocalVariable("i", INT_TYPE)
        val sResult = LocalVariable("result", INT_TYPE)
        val conditional = ConditionalExpression.create(
            GreaterEqualConstr(sInput, IntValue(0)),
            sInput,
            MultiplicationExpr(sInput, IntValue(-1))
        )

        val sILoopResult =
            FunctionApplication(
                FunctionSymbol("i_AFTER_LOOP_0", INT_TYPE, listOf(INT_TYPE, INT_TYPE, INT_TYPE)),
                listOf(
                    FunctionApplication(
                        FunctionSymbol("iterations_LOOP_0", INT_TYPE, listOf(INT_TYPE, INT_TYPE)),
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
                FunctionSymbol("i_ANON_LOOP_0", INT_TYPE, listOf(INT_TYPE, INT_TYPE, INT_TYPE)),
                listOf(
                    LocalVariable("itCnt_LOOP_0", INT_TYPE),
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

        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.SimpleMethods",
            "int simpleLoop(int)"
        )

        analysis.symbolicallyExecute()
        compareLeaves(expectedLeaves, analysis)
        compareLoopLeaves(expectedLoopLeaves, analysis)
    }

    // TODO: Add test case for nested loop, maybe even w/ labeled break

    @Test
    fun testSimpleLoopWithContinueAndBreak() {
        val sInput = LocalVariable("input", INT_TYPE)
        val sI = LocalVariable("i", INT_TYPE)
        val sResult = LocalVariable("result", INT_TYPE)
        val conditional = ConditionalExpression.create(
            GreaterEqualConstr(sInput, IntValue(0)),
            sInput,
            MultiplicationExpr(sInput, IntValue(-1))
        )

        val sILoopResult =
            FunctionApplication(
                FunctionSymbol("i_AFTER_LOOP_0", INT_TYPE, listOf(INT_TYPE, INT_TYPE, INT_TYPE)),
                listOf(
                    FunctionApplication(
                        FunctionSymbol("iterations_LOOP_0", INT_TYPE, listOf(INT_TYPE, INT_TYPE)),
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
                FunctionSymbol("i_ANON_LOOP_0", INT_TYPE, listOf(INT_TYPE, INT_TYPE, INT_TYPE)),
                listOf(
                    LocalVariable("itCnt_LOOP_0", INT_TYPE),
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

        val analysis = SymbolicExecutionAnalysis.create(
            "de.dominicsteinhoefel.pluggabl.SimpleMethods",
            "int simpleLoopWithContinueAndBreak(int)"
        )

        analysis.symbolicallyExecute()
        compareLeaves(expectedLeaves, analysis)
        compareLoopLeaves(expectedLoopLeaves, analysis)
    }
}