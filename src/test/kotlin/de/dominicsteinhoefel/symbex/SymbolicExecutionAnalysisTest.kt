package de.dominicsteinhoefel.symbex

import de.dominicsteinhoefel.symbex.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.symbex.analysis.SymbolicExecutionAnalysisTransformer
import de.dominicsteinhoefel.symbex.expr.*
import de.dominicsteinhoefel.symbex.transformation.CutLoopTransformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import soot.*
import soot.toolkits.graph.ExceptionalUnitGraph
import soot.toolkits.graph.UnitGraph
import kotlin.math.sin

class SymbolicExecutionAnalysisTest {
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

    companion object {
        private fun compareLeaves(expected: List<SymbolicExecutionState>) =
            fun(a: SymbolicExecutionAnalysis, graph: UnitGraph) {
                val results = graph.tails.map { a.getFlowBefore(it) }
                assertEquals(expected.size, results.size)
                for (exp in expected) {
                    assertTrue("$exp not contained in ${results.joinToString(", ")}", results.contains(exp))
                }
            }

        private val printSESs = fun(a: SymbolicExecutionAnalysis, graph: UnitGraph) {
            for (node in graph) {
                println("Node \"$node\":")
                println("Flow before:        ${a.getFlowBefore(node)}")
                println("Fall flow after:    ${a.getFallFlowAfter(node)}")
                println("Branch flows after: ${a.getBranchFlowAfter(node).map { it.toString() }.joinToString(", ")}\n")
            }
        }

        fun symbolicallyExecuteMethod(
            clazz: String,
            methodSig: String,
            postProcess: (SymbolicExecutionAnalysis, UnitGraph) -> Unit = { _, _ -> }
        ) {
            G.reset()

            val cutLoopTransform = Transform("jtp.cutloop", CutLoopTransformation)
            cutLoopTransform.declaredOptions = CutLoopTransformation.getDeclaredOptions()

            val seAnalysis = Transform("jtp.symbolicexecution", SymbolicExecutionAnalysisTransformer(postProcess))
            seAnalysis.declaredOptions = SymbolicExecutionAnalysisTransformer.getDeclaredOptions()

            PackManager.v().getPack("jtp").let {
                it.add(cutLoopTransform)
                it.add(seAnalysis)
            }

            Scene.v().sootClassPath = "./build/classes/kotlin/test"
            Scene.v().extendSootClassPath("./lib/kotlin-stdlib-1.3.72.jar")
            Scene.v().extendSootClassPath("/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar")

            PhaseOptions.v().setPhaseOption("jb", "use-original-names");
            PhaseOptions.v().setPhaseOption("jtp.cutloop", "class:$clazz");
            PhaseOptions.v().setPhaseOption("jtp.cutloop", "method:$methodSig");
            PhaseOptions.v().setPhaseOption("jtp.symbolicexecution", "class:$clazz");
            PhaseOptions.v().setPhaseOption("jtp.symbolicexecution", "method:$methodSig");

            // Add a line like the following when using JRE classes in the future:
            // Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);

            Main.main(arrayOf(clazz))
        }
    }
}