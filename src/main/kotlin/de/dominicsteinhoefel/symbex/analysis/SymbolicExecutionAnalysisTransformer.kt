package de.dominicsteinhoefel.symbex.analysis

import soot.Body
import soot.BodyTransformer
import soot.toolkits.graph.ExceptionalUnitGraph
import soot.toolkits.graph.UnitGraph

class SymbolicExecutionAnalysisTransformer(
    val postProcess: (SymbolicExecutionAnalysis, UnitGraph) -> Unit = { _, _ -> }
) : BodyTransformer() {

    override fun internalTransform(body: Body, phase: String, options: Map<String, String>) {
        val clazz = options["class"]
        val methodSig = options["method"]
        val methodsToAnalyze = arrayOf("<${clazz}: ${methodSig}>")

        if (methodsToAnalyze.contains(body.method.signature)) {
            val graph = ExceptionalUnitGraph(body)
            val analysis = SymbolicExecutionAnalysis(graph)
            postProcess(analysis, graph)
        }
    }

    companion object {
        fun getDeclaredOptions() = "class method"
    }

}