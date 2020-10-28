package de.dominicsteinhoefel.symbex

import de.dominicsteinhoefel.symbex.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.symbex.analysis.SymbolicExecutionAnalysisTransformer
import de.dominicsteinhoefel.symbex.transformation.CutLoopTransformation
import org.junit.Assert.assertEquals
import org.junit.Test
import soot.*
import soot.toolkits.graph.ExceptionalUnitGraph
import soot.toolkits.graph.UnitGraph

class SymbolicExecutionAnalysisTest {
    @Test
    fun testSimpleTwoBranchedMethod() {
        val postProcessing = fun(a: SymbolicExecutionAnalysis, graph: UnitGraph) {
            val expected = mapOf(
                "return \$stack3" to
                        "({!(((input)+(1))==(42))}, [test -> (input)+(1)]++[\$stack3 -> ((input)+(1))+(3)])",
                "return test" to
                        "({((input)+(1))==(42)}, [test -> (((input)+(1))+(2))+(4)])"
            )

            val result = graph.tails.associateWith { a.getFlowBefore(it).toString() }.mapKeys { it.key.toString() }

            assertEquals(expected, result)
        }

        symbolicallyExecuteMethod(
            "de.dominicsteinhoefel.symbex.SimpleMethods",
            "int simpleTwoBranchedMethod(int)",
            postProcessing
        )
    }


    @Test
    fun testSimpleTwoBranchedMethodWithMerge() {
        val postProcessing = fun(a: SymbolicExecutionAnalysis, graph: UnitGraph) {
            val expected = mapOf(
                "return test" to
                        "({}, [test -> (if (((input)+(1))==(42)) then (((input)+(1))+(2)) else (((input)+(1))+(3)))+(4)])"
            )

            val result = graph.tails.associateWith { a.getFlowBefore(it).toString() }.mapKeys { it.key.toString() }

            assertEquals(expected, result)
        }

        symbolicallyExecuteMethod(
            "de.dominicsteinhoefel.symbex.SimpleMethods",
            "int simpleTwoBranchedMethodWithMerge(int)",
            postProcessing
        )
    }

    @Test
    fun testSimpleLoop() {
        symbolicallyExecuteMethod(
            "de.dominicsteinhoefel.symbex.SimpleMethods",
            "int simpleLoop(int)",
            printSESs
        )
    }

    companion object {
        private val printSESs = fun (a: SymbolicExecutionAnalysis, graph: UnitGraph) {
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

            Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);

            Main.main(arrayOf(clazz))
        }
    }
}