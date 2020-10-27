package de.dominicsteinhoefel.symbex

import org.junit.Test
import soot.*
import soot.toolkits.graph.ExceptionalUnitGraph

class SymbolicExecutionAnalysisTest {
    @Test
    fun testSimpleMethod() {
        val methodsToAnalyze = arrayOf("<de.dominicsteinhoefel.symbex.SimpleMethod: int simpleMethod(int)>")

        val transf = object : BodyTransformer() {
            override fun internalTransform(body: Body, phase: String, options: Map<String, String>) {
                if (methodsToAnalyze.contains(body.method.signature)) {
                    SymbolicExecutionAnalysis(ExceptionalUnitGraph(body))
                }
            }
        }

        PackManager.v().getPack("jtp").add(
            Transform("jtp.symbolicexecution", transf)
        )

        Main.main(
            arrayOf(
                "-cp",
                "./build/classes/kotlin/test:./lib/kotlin-stdlib-1.3.72.jar:/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar",
                "de.dominicsteinhoefel.symbex.SimpleMethod"
            )
        )
    }
}