package de.dominicsteinhoefel.symbex.analysis

import de.dominicsteinhoefel.symbex.expr.*
import de.dominicsteinhoefel.symbex.util.SootBridge
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import soot.*
import soot.jimple.Stmt
import soot.toolkits.graph.ExceptionalUnitGraph
import java.util.*
import kotlin.collections.HashMap

class SymbolicExecutionAnalysis(clazz: String, methodSig: String) {
    private val body: Body
    private val stateToInputSESMap = HashMap<Stmt, List<SymbolicExecutionState>>()
    private val stateToOutputSESMap = HashMap<Stmt, List<SymbolicExecutionState>>()

    val cfg: ExceptionalUnitGraph

    private val rules = arrayOf(AssignRule, IfRule, LeafRule, DummyRule, IgnoreAndWarnRule)

    init {
        G.reset()

        body = SootBridge.loadJimpleBody(clazz, methodSig)
            ?: throw IllegalStateException("Could not load method $methodSig of class $clazz")
        cfg = ExceptionalUnitGraph(body)
    }

    fun symbolicallyExecute() {
        val rootStmt = cfg.heads[0] as Stmt
        val queue = LinkedList<Stmt>()

        fun <E> LinkedList<E>.addLastDistinct(elem: E) {
            if (!contains(elem)) addLast(elem)
        }

        queue.add(rootStmt)
        stateToInputSESMap[rootStmt] = listOf(SymbolicExecutionState())

        while (!queue.isEmpty()) {
            val currStmt = queue.pop()
            val inputStates = stateToInputSESMap[currStmt] ?: emptyList()

            if (cfg.getPredsOf(currStmt).size > inputStates.size) {
                // Evaluation of predecessors not yet finished
                logger.trace(
                    "Postponing evaluation of statement ${currStmt}, " +
                            "${cfg.getPredsOf(currStmt).size - inputStates.size} predecessors not yet evaluated."
                )

                queue.addLast(currStmt)
            }

            val rule = getApplicableRule(currStmt, inputStates)
            val result = rule.apply(currStmt, inputStates).map { it.simplify() }

            if (result.size != cfg.getSuccsOf(currStmt).size) {
                throw IllegalStateException(
                    "Expected number ${result.size} of successors in SE graph " +
                            "does not match number of successors in CFG (${cfg.getSuccsOf(currStmt).size})"
                )
            }

            stateToOutputSESMap[currStmt] = result

            cfg.getSuccsOf(currStmt).zip(result).forEach { (cfgNode, ses) ->
                (cfgNode as Stmt).let {
                    stateToInputSESMap[it] = listOf(
                        stateToInputSESMap[it] ?: emptyList(),
                        listOf(ses)
                    ).flatten()

                    queue.addLastDistinct(cfgNode)
                }
            }
        }
    }

    fun getInputSESs(node: Stmt) = stateToInputSESMap[node] ?: emptyList()
    fun getOutputSESs(node: Stmt) = stateToOutputSESMap[node] ?: emptyList()

    private fun getApplicableRule(
        currStmt: Stmt,
        inputStates: List<SymbolicExecutionState>
    ): SERule {
        val applicableRules = rules.filter { it.accepts(currStmt, inputStates) }
        logger.trace(
            "Applicable rules for statement type ${currStmt::class.simpleName}, " +
                    "${inputStates.size} input states: " +
                    applicableRules.joinToString(", ")
        )

        if (applicableRules.size != 1) {
            throw IllegalStateException(
                "Expected exactly one applicable rules for statement type " +
                        "${currStmt::class.simpleName}, found ${applicableRules.size}"
            )
        }

        return applicableRules[0]
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SymbolicExecutionAnalysis::class.simpleName)

        @JvmStatic
        fun main(args: Array<String>) {
            val engine = SymbolicExecutionAnalysis(
                "de.dominicsteinhoefel.symbex.SimpleMethods",
                "int simpleTwoBranchedMethodWithMerge(int)"
            )

            engine.symbolicallyExecute()
        }
    }
}