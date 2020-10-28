package de.dominicsteinhoefel.symbex.analysis

import de.dominicsteinhoefel.symbex.expr.*
import org.slf4j.LoggerFactory
import soot.Unit
import soot.jimple.internal.*
import soot.toolkits.graph.UnitGraph
import soot.toolkits.scalar.ForwardBranchedFlowAnalysis

/**
 * A full symbolic execution analysis. Note that it will generally not terminate when facing loops with symbolic guards;
 * for these use cases, you have to apply a suitable program transformation prior to using this analysis, e.g.,
 * CutLoopTransformation.
 *
 * @author Dominic Steinhoefel
 */
class SymbolicExecutionAnalysis(private val graph: UnitGraph) :
    ForwardBranchedFlowAnalysis<SymbolicExecutionState>(graph) {

    init {
        doAnalysis()
    }

    override fun newInitialFlow() = SymbolicExecutionState()

    override fun merge(in1: SymbolicExecutionState?, in2: SymbolicExecutionState?, out: SymbolicExecutionState?) {
        if (in1 != null && in2 != null) out?.mergeFromSESs(ses1 = in1, ses2 = in2)
    }

    override fun copy(source: SymbolicExecutionState?, dest: SymbolicExecutionState?) {
        if (source != null) {
            dest?.store = source.store
            dest?.constraints = source.constraints
        }
    }

    override fun flowThrough(
        inp: SymbolicExecutionState?,
        s: Unit?,
        fallOut: MutableList<SymbolicExecutionState>?,
        branchOuts: MutableList<SymbolicExecutionState>?
    ) {
        if (inp == null) return

        when (s) {
            is JAssignStmt -> fallOut?.get(0)?.addAssignment(
                ExprConverter.convert(s.leftOp) as Symbol,
                ExprConverter.convert(s.rightOp)
            )
            is JIfStmt -> {
                val constraint = ConstrConverter.convert(s.condition)
                branchOuts?.get(0)?.addConstraint(constraint)
                fallOut?.get(0)?.addConstraint(NegatedConstr.create(constraint))

            }
            is JReturnStmt, is JIdentityStmt, is JGotoStmt -> {
                print("")
            }
            is JInvokeStmt -> {
                logger.warn("Ignoring JInvokeStmt ${s} for now, have to handle appropriately soon!")
            }
            else -> {
                TODO("Execution of Unit type ${s?.javaClass} not yet implemented")
            }
        }

        fallOut?.forEach { it.apply(inp).simplify() }
        branchOuts?.forEach { it.apply(inp).simplify() }
    }

    companion object {
        val logger = LoggerFactory.getLogger(SymbolicExecutionAnalysis::class.simpleName)
    }
}