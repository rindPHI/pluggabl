package de.dominicsteinhoefel.symbex

import org.junit.Assert.assertEquals
import org.junit.Test
import soot.*
import soot.jimple.toolkits.annotation.logic.LoopFinder
import soot.toolkits.graph.ExceptionalUnitGraph
import soot.toolkits.graph.UnitGraph

class SymbolicExecutionAnalysisTest {
    @Test
    fun testSimpleTwoBranchedMethod() {
        val postProcessing = fun(a: SymbolicExecutionAnalysis, graph: UnitGraph) {
            val expected = mapOf(
                "return \$i1" to
                        "({!(((i0)+(1))==(42))}, [i3 -> (i0)+(1)]++[\$i1 -> ((i0)+(1))+(3)])",
                "return i5" to
                        "({((i0)+(1))==(42)}, [i3 -> (i0)+(1)]++[i4 -> ((i0)+(1))+(2)]++[i5 -> (((i0)+(1))+(2))+(4)])"
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
    fun testSimpleLoop() {
        symbolicallyExecuteMethod(
            "de.dominicsteinhoefel.symbex.SimpleMethods",
            "int simpleLoop(int)"
        )
    }

    companion object {
        fun symbolicallyExecuteMethod(
            clazz: String,
            methodSig: String,
            postProcess: (SymbolicExecutionAnalysis, UnitGraph) -> Unit = { _, _ -> }
        ) {
            val methodsToAnalyze = arrayOf("<${clazz}: ${methodSig}>")

            val transformation = object : BodyTransformer() {
                override fun internalTransform(body: Body, phase: String, options: Map<String, String>) {
                    if (methodsToAnalyze.contains(body.method.signature)) {

                        // TODO: Implement below sketch correctly, maybe as a separate transformer
                        // SKETCH {
                        // Remove all back edges from loops to render SE finite
                        // TODO: We also have to anonymize loop variables: At the beginning
                        //       of the body as well as after the back edge
                        for (loop in LoopFinder().getLoops(body)) {
                            val node = loop.backJumpStmt
                            val oldTarget = loop.head
                            val newTarget = loop.head.unitBoxes[0]

                            for (targetBox in node.unitBoxes) {
                                val target = targetBox.unit
                                if (target === oldTarget) {
                                    targetBox.unit = newTarget.unit
                                }
                            }
                        }
                        // } END SKETCH

                        val graph = ExceptionalUnitGraph(body)
                        val analysis = SymbolicExecutionAnalysis(graph)
                        postProcess(analysis, graph)
                    }
                }
            }

            PackManager.v().getPack("jtp").add(
                Transform("jtp.symbolicexecution", transformation)
            )

            Scene.v().sootClassPath = "./build/classes/kotlin/test"
            Scene.v().extendSootClassPath("./lib/kotlin-stdlib-1.3.72.jar")
            Scene.v().extendSootClassPath("/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar")

            Main.main(arrayOf(clazz))
        }
    }
}