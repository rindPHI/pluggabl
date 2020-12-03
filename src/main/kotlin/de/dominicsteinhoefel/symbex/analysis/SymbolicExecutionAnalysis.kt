package de.dominicsteinhoefel.symbex.analysis

import de.dominicsteinhoefel.symbex.expr.*
import de.dominicsteinhoefel.symbex.util.NewNamesCreator
import de.dominicsteinhoefel.symbex.util.SootBridge
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import soot.Body
import soot.G
import soot.Local
import soot.jimple.Stmt
import soot.jimple.toolkits.annotation.logic.Loop
import soot.jimple.toolkits.annotation.logic.LoopFinder
import soot.toolkits.graph.ExceptionalUnitGraph
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashSet

class SymbolicExecutionAnalysis private constructor(
    private val body: Body,
    private val root: Stmt = body.units.first as Stmt,
    private val stopAtNodes: List<Stmt> = emptyList(),
    private val ignoreTopLoop: Boolean = false
) {
    val cfg: ExceptionalUnitGraph = ExceptionalUnitGraph(body)

    private val stmtToInputSESMap = HashMap<Stmt, List<SymbolicExecutionState>>()
    private val stmtToOutputSESMap = HashMap<Stmt, List<SymbolicExecutionState>>()
    private val newNamesCreator = NewNamesCreator()
    private val loops: Set<Loop> = LoopFinder().getLoops(body)

    private val rules = arrayOf(AssignRule, IfRule, LeafRule, DummyRule, IgnoreAndWarnRule)

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SymbolicExecutionAnalysis::class.simpleName)

        fun create(clazz: String, methodSig: String): SymbolicExecutionAnalysis {
            G.reset()
            return SymbolicExecutionAnalysis(
                SootBridge.loadJimpleBody(clazz, methodSig)
                    ?: throw IllegalStateException("Could not load method $methodSig of class $clazz")
            )
        }

        fun create(
            body: Body,
            root: Stmt = body.units.first as Stmt,
            forceLeaves: List<Stmt> = emptyList(),
            ignoreTopLoop: Boolean = false
        ): SymbolicExecutionAnalysis {
            G.reset()
            return SymbolicExecutionAnalysis(body, root, forceLeaves, ignoreTopLoop)
        }
    }

    fun getInputSESs(node: Stmt) = stmtToInputSESMap[node] ?: emptyList()
    fun getOutputSESs(node: Stmt) = stmtToOutputSESMap[node] ?: emptyList()

    fun symbolicallyExecute() {
        val queue = LinkedList<Stmt>()

        fun <E> LinkedList<E>.addLastDistinct(elem: E) {
            if (!contains(elem)) addLast(elem)
        }

        queue.add(root)
        stmtToInputSESMap[root] = listOf(SymbolicExecutionState())

        while (!queue.isEmpty()) {
            val currStmt = queue.pop()

            val assocLoop = loops.filter { it.head == currStmt }.let { if (it.size == 1) it[0] else null }

            if (assocLoop != null && !(ignoreTopLoop && assocLoop.head == root)) {
                executeLoopAndAnonymize(assocLoop)
                queue.addAll(assocLoop.loopExits.map { cfg.getSuccsOf(it) }.flatten().map { it as Stmt }
                    .filterNot(assocLoop.loopStatements::contains))
                continue
            }

            val inputStates = (stmtToInputSESMap[currStmt] ?: emptyList())

            if (cfg.getPredsOf(currStmt).size - (if (assocLoop != null) 1 else 0) > inputStates.size) {
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

            stmtToOutputSESMap[currStmt] = result

            // Only propagate result state if currStmt is no back edge to a loop header
            if (!loops.any { it.backJumpStmt == currStmt }) {
                propagateResultStateToSuccs(currStmt, result)

                if (!stopAtNodes.contains(currStmt)) {
                    cfg.getSuccsOf(currStmt).map { it as Stmt }.forEach(queue::addLastDistinct)
                }
            }

            // But propagate if we're currently doing a loop analysis
            if (ignoreTopLoop && loops.any { it.backJumpStmt == currStmt && it.head == root }) {
                propagateResultStateToSuccs(currStmt, result)
            }
        }
    }

    private fun propagateResultStateToSuccs(
        currStmt: Stmt,
        result: List<SymbolicExecutionState>,
        ignore: List<Stmt> = emptyList()
    ) {
        cfg.getSuccsOf(currStmt).map { it as Stmt }
            .filterNot(ignore::contains).zip(result).forEach { (cfgNode, ses) ->
                stmtToInputSESMap[cfgNode] = listOf(
                    stmtToInputSESMap[cfgNode] ?: emptyList(),
                    listOf(ses)
                ).flatten()
            }
    }

    private fun executeLoopAndAnonymize(loop: Loop) {
        val writtenVars = loop.loopStatements.map { it.defBoxes }.flatten().map { it.value }

        if (writtenVars.any { it !is Local }) {
            throw NotImplementedError("Anonymization of written heap locations currently not implemented")
        }

        val loopAnalysis = SymbolicExecutionAnalysis(
            body,
            loop.head,
            listOf(
                loop.loopExits.toList().filterNot(loop.head::equals),
                cfg.getSuccsOf(loop.head).map { it as Stmt }.filterNot(loop.loopStatements::contains)
                //,listOf(loop.backJumpStmt)
            ).flatten(),
            true
        )

        loopAnalysis.symbolicallyExecute()

        // merge all states of all loop exits
        // TODO: Check if this works as is for abrupt completion (breaks)
        val resultState = SymbolicExecutionState.merge(
            loopAnalysis.stmtToInputSESMap.filter { loop.loopExits.contains(it.key) }
                .map { it.value }.flatten().filterNot(SymbolicExecutionState::isEmpty)
        )

        val initState = SymbolicExecutionState.merge(stmtToInputSESMap[loop.head] ?: emptyList())

        val anonymizingStore =
            writtenVars.map { ExprConverter.convert(it) as LocalVariable }
                .associateWith { writtenVar ->
                    val relVars = variablesRelevantFor(writtenVar, initState, resultState)
                    FunctionApplication(
                        FunctionSymbol(
                            newNamesCreator.newName(writtenVar.name + "_ANON_LOOP"),
                            writtenVar.type,
                            relVars.map { it.type }
                        ),
                        relVars.toList()
                    )
                }.map { ElementaryStore(it.key, it.value) }
                .fold(EmptyStore as SymbolicStore,
                    { acc, elem -> ParallelStore.create(acc, elem) })

        val anonymizingState = SymbolicExecutionState(emptySet(), anonymizingStore)

        for (stmt in loop.loopStatements) {
            val loopAnalysisInputSESs =
                if (stmt == loop.head) listOf(SymbolicExecutionState()) // Remove back-propagated state
                else loopAnalysis.getInputSESs(stmt)
            stmtToInputSESMap[stmt] =
                loopAnalysisInputSESs.map { it.apply(anonymizingState).apply(initState).simplify() }
            stmtToOutputSESMap[stmt] =
                loopAnalysis.getOutputSESs(stmt).map { it.apply(anonymizingState).apply(initState).simplify() }

            if (loop.loopExits.contains(stmt)) {
                propagateResultStateToSuccs(
                    stmt,
                    stmtToOutputSESMap[stmt]?.reversed() ?: emptyList(),
                    loop.loopStatements
                )
            }
        }
    }

    private fun variablesRelevantFor(
        lv: LocalVariable,
        initState: SymbolicExecutionState,
        finalState: SymbolicExecutionState
    ): Set<LocalVariable> {
        val rhsExpression =
            SymbolicStore.elementaries(finalState.store).filter { it.lhs == lv }.let {
                if (it.size != 1)
                    throw IllegalStateException("Expected exactly one assignment of variable $lv in store, found ${it.size}")
                it
            }[0].rhs

        val result = LinkedHashSet<LocalVariable>()

        result.addAll(rhsExpression.accept(LocalVariableExpressionCollector()))

        while (true) {
            val newLocals = finalState.constraints.associateWith { it.accept(LocalVariableConstraintCollector()) }
                .filter { it.value.any(result::contains) }.map { it.value }.flatten().toSet()

            if (result.containsAll(newLocals)) {
                break
            }

            result.addAll(newLocals)
        }

        // Remove all that are not assigned before and are not method parameters

        result.retainAll(
            listOf(
                initState.constraints.map { it.accept(LocalVariableConstraintCollector()) }.flatten(),
                initState.store.accept(LocalVariableStoreCollector()),
                body.parameterLocals.map { ExprConverter.convert(it) as LocalVariable }
            ).flatten()
        )

        return result
    }

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
}