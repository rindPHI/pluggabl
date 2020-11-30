package de.dominicsteinhoefel.symbex.analysis

import de.dominicsteinhoefel.symbex.expr.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import soot.*
import soot.jimple.Stmt
import soot.jimple.internal.*
import soot.toolkits.graph.ExceptionalUnitGraph
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class SymbolicExecutionEngine(private val clazz: String, val methodSig: String) {
    private val body: Body
    private val graph: ExceptionalUnitGraph
    private val stateToInputSESMap = HashMap<Stmt, List<SymbolicExecutionState>>()

    private val rules = arrayOf(AssignRule, IfRule, DummyRule, IgnoreAndWarnRule)

    init {
        G.reset()

        body = loadJimpleBody(clazz, methodSig)
            ?: throw IllegalStateException("Could not load method $methodSig of class $clazz")
        graph = ExceptionalUnitGraph(body)
    }

    fun symbolicallyExecute() {
        val rootStmt = graph.heads[0] as Stmt
        val queue = LinkedList<Stmt>()

        queue.add(rootStmt)
        stateToInputSESMap[rootStmt] = listOf(SymbolicExecutionState())

        while (!queue.isEmpty()) {
            val currStmt = queue.pop()
            val inputStates = stateToInputSESMap[currStmt] ?: emptyList()

            if (graph.getPredsOf(currStmt).size > inputStates.size) {
                // Evaluation of predecessors not yet finished
                logger.trace(
                    "Postponing evaluation of statement ${currStmt}, " +
                            "${graph.getPredsOf(currStmt).size - inputStates.size} predecessors not yet evaluated."
                )

                queue.addLast(currStmt)
            }

            val rule = getApplicableRule(currStmt, inputStates)
            val result = rule.apply(currStmt, inputStates)

            if (result.size != graph.getSuccsOf(currStmt).size) {
                throw IllegalStateException(
                    "Expected number ${result.size} of successors in SE graph " +
                            "does not match number of successors in CFG (${graph.getSuccsOf(currStmt).size})"
                )
                // result.forEach { println(it) }
                // TODO
                return
            }

            graph.getSuccsOf(currStmt).zip(result).forEach { (cfgNode, ses) ->
                (cfgNode as Stmt).let {
                    if (stateToInputSESMap[it] == null) {
                        stateToInputSESMap[it] = listOf(ses)
                    } else {
                        stateToInputSESMap[it] =
                            listOf(stateToInputSESMap[it] as List<SymbolicExecutionState>, listOf(ses)).flatten()
                    }

                    queue.addLast(cfgNode)
                }
            }
        }
    }

    private fun getApplicableRule(
        currStmt: Stmt,
        inputStates: List<SymbolicExecutionState>
    ): SERule {
        val applicableRules = rules.filter { it.accepts(currStmt, inputStates) }
        logger.trace(
            "Applicable rules for statement type ${currStmt::class.simpleName}, " +
                    "${inputStates.size} input states: " +
                    "${applicableRules.joinToString(", ")}"
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
        val logger: Logger = LoggerFactory.getLogger(SymbolicExecutionEngine::class.simpleName)

        private fun loadJimpleBody(clazz: String, methodSig: String): Body? {
            val sig = "<${clazz}: ${methodSig}>"
            var body: Body? = null

            val seAnalysis = Transform("jtp.symbolicexecution", object : BodyTransformer() {
                override fun internalTransform(b: Body?, phaseName: String?, options: MutableMap<String, String>?) {
                    if (b == null) return

                    if (b.method.signature == sig) {
                        body = b
                    }
                }
            })

            PackManager.v().getPack("jtp").let {
                it.add(seAnalysis)
            }

            Scene.v().sootClassPath = "./build/classes/kotlin/test"
            Scene.v().extendSootClassPath("./lib/kotlin-stdlib-1.3.72.jar")
            Scene.v().extendSootClassPath("/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar")
            Scene.v().extendSootClassPath("/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/jce.jar")

            PhaseOptions.v().setPhaseOption("jb", "use-original-names");

            // Add a line like the following when using JRE classes in the future:
            // Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);

            Main.main(arrayOf(clazz))

            return body
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val engine = SymbolicExecutionEngine(
                "de.dominicsteinhoefel.symbex.SimpleMethods",
                "int simpleTwoBranchedMethodWithMerge(int)"
            )

            engine.symbolicallyExecute()
        }
    }

    interface SERule {
        fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>): Boolean
        fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>): List<SymbolicExecutionState>
    }

    object AssignRule : SERule {
        override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
            stmt is JAssignStmt

        override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
            (stmt as JAssignStmt).let {
                listOf(
                    SymbolicExecutionState.merge(inpStates).addAssignment(
                        ExprConverter.convert(it.leftOp) as Symbol,
                        ExprConverter.convert(it.rightOp)
                    )
                )
            }

        override fun toString() = "AssignRule"
    }

    object IfRule : SERule {
        override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
            stmt is JIfStmt

        override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
            (ConstrConverter.convert((stmt as JIfStmt).condition)).let { constraint ->
                (SymbolicExecutionState.merge(inpStates)).let {
                    listOf(
                        it.addConstraint(constraint),
                        it.addConstraint(NegatedConstr.create(constraint))
                    )
                }
            }

        override fun toString() = "IfRule"
    }

    object DummyRule : SERule {
        override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
            stmt is JReturnStmt || stmt is JIdentityStmt || stmt is JGotoStmt

        override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
            listOf(SymbolicExecutionState.merge(inpStates))

        override fun toString() = "DummyRule"
    }

    object IgnoreAndWarnRule : SERule {
        override fun accepts(stmt: Stmt, inpStates: List<SymbolicExecutionState>) =
            stmt is JInvokeStmt

        override fun apply(stmt: Stmt, inpStates: List<SymbolicExecutionState>): List<SymbolicExecutionState> {
            LoggerFactory.getLogger(this::class.simpleName)
                .warn("Ignoring JInvokeStmt $stmt for now, have to handle appropriately soon!")
            return listOf(SymbolicExecutionState.merge(inpStates))
        }

        override fun toString() = "IgnoreAndWarnRule"
    }
}