package de.dominicsteinhoefel.symbex

import de.dominicsteinhoefel.symbex.analysis.SymbolicExecutionAnalysis
import de.dominicsteinhoefel.symbex.analysis.SymbolicExecutionAnalysisTransformer
import de.dominicsteinhoefel.symbex.expr.SymbolicExecutionState
import de.dominicsteinhoefel.symbex.transformation.CutLoopTransformation
import org.junit.Assert
import soot.*
import soot.options.Options
import soot.toolkits.graph.UnitGraph

object SymbolicExecutionTestHelper {
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
        Scene.v().extendSootClassPath("/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/jce.jar")

        PhaseOptions.v().setPhaseOption("jb", "use-original-names");
        PhaseOptions.v().setPhaseOption("jtp.cutloop", "class:$clazz");
        PhaseOptions.v().setPhaseOption("jtp.cutloop", "method:$methodSig");
        PhaseOptions.v().setPhaseOption("jtp.symbolicexecution", "class:$clazz");
        PhaseOptions.v().setPhaseOption("jtp.symbolicexecution", "method:$methodSig");

        // Add a line like the following when using JRE classes in the future:
        // Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);

        Main.main(arrayOf(clazz))
    }

    fun compareLeaves(expected: List<SymbolicExecutionState>) =
        fun(a: SymbolicExecutionAnalysis, graph: UnitGraph) {
            val results = graph.tails.map { a.getFlowBefore(it) }
            Assert.assertEquals(expected.size, results.size)
            for (exp in expected) {
                Assert.assertTrue("$exp not contained in ${results.joinToString(", ")}", results.contains(exp))
            }
        }

    fun printSESs() = fun(a: SymbolicExecutionAnalysis, graph: UnitGraph) {
        for (node in graph) {
            println("Node \"$node\":")
            println("Flow before:        ${a.getFlowBefore(node)}")
            println("Fall flow after:    ${a.getFallFlowAfter(node)}")
            println("Branch flows after: ${a.getBranchFlowAfter(node).map { it.toString() }.joinToString(", ")}\n")
        }
    }

    fun printLeafSESs() = fun(a: SymbolicExecutionAnalysis, graph: UnitGraph) {
        for (node in graph.tails) {
            println("Node \"$node\":")
            println(a.getFlowBefore(node))
            println()
        }
    }
}