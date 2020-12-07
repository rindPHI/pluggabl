package de.dominicsteinhoefel.symbex.analysis

import de.dominicsteinhoefel.symbex.expr.*
import de.dominicsteinhoefel.symbex.util.NewNamesCreator
import de.dominicsteinhoefel.symbex.util.SootBridge
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import soot.Body
import soot.G
import soot.Local
import soot.Value
import soot.jimple.GotoStmt
import soot.jimple.IfStmt
import soot.jimple.Stmt
import soot.jimple.toolkits.annotation.logic.Loop
import soot.jimple.toolkits.annotation.logic.LoopFinder
import soot.toolkits.graph.ExceptionalUnitGraph
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap
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
    private val loops = LoopFinder().getLoops(body)
    private val loopLeafSESMap = LinkedHashMap<Loop, SymbolicExecutionState>()

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
    fun getLoopLeafSESs() = loopLeafSESMap.toMap()

    fun symbolicallyExecute() {
        val queue = LinkedList<Stmt>()

        fun <E> LinkedList<E>.addLastDistinct(elem: E) {
            if (!contains(elem)) addLast(elem)
        }

        queue.add(root)
        stmtToInputSESMap[root] = listOf(SymbolicExecutionState())

        while (!queue.isEmpty()) {
            val currStmt = queue.pop()

            if (stopAtNodes.contains(currStmt)) {
                continue
            }

            val assocLoop = loops.filter { it.head == currStmt }.let { if (it.size == 1) it[0] else null }

            val stmtIsHeadOfAnalyzedLoop = ignoreTopLoop && assocLoop?.head == root

            if (assocLoop != null && !stmtIsHeadOfAnalyzedLoop) {
                executeLoopAndAnonymize(assocLoop)
                queue.addAll(assocLoop.loopExits.map { cfg.getSuccsOf(it) }.flatten().map { it as Stmt }
                    .filterNot(assocLoop.loopStatements::contains))
                continue
            }

            val inputStates = (stmtToInputSESMap[currStmt] ?: emptyList())

            if (cfg.getPredsOf(currStmt).size > inputStates.size && !stmtIsHeadOfAnalyzedLoop) {
                // Evaluation of predecessors not yet finished
                logger.trace(
                    "Postponing evaluation of statement ${currStmt}, " +
                            "${cfg.getPredsOf(currStmt).size - inputStates.size} predecessors not yet evaluated."
                )

                queue.addLast(currStmt)
                continue
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
            if (!(currStmt is GotoStmt && loops.any { it.head == currStmt.target })) {
                propagateResultStateToSuccs(currStmt, result)
                cfg.getSuccsOf(currStmt).map { it as Stmt }.forEach(queue::addLastDistinct)
            }

            // But propagate if we're currently doing a loop analysis
            if (ignoreTopLoop && (currStmt is GotoStmt && loops.any { it.head == currStmt.target && it.head == root })) {
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
        val loopIdx = loops.indexOf(loop)

        logger.trace("Analyzing loop ${loopIdx + 1}")

        val writtenVars = loop.loopStatements.asSequence().map { it.defBoxes }.flatten().map { it.value }
            .map {
                if (it !is Local) {
                    throw NotImplementedError("Anonymization of written heap locations currently not implemented")
                }
                it
            }
            .map { ExprConverter.convert(it) as LocalVariable }.toSet().toList()

        val stmtsAfterExit = loop.loopExits.map {
            if (it is GotoStmt) it.target else {
                if (it is IfStmt) it.target else null
            }
        }.filterNot { it == null }.filterNot { loop.loopStatements.contains(it) }.map { it as Stmt }

        val loopAnalysis = SymbolicExecutionAnalysis(
            body,
            loop.head,
            stmtsAfterExit,
            true
        )

        loopAnalysis.symbolicallyExecute()

        // merge all states of all loop exits
        val resultState = SymbolicExecutionState.merge(
            loopAnalysis.stmtToInputSESMap.filter { loop.loopExits.contains(it.key) }
                .map { it.value }.flatten().filterNot(SymbolicExecutionState::isEmpty)
        )

        val initState = SymbolicExecutionState.merge(stmtToInputSESMap[loop.head] ?: emptyList())
        val loopCntVar = LocalVariable(newNamesCreator.newName("itCnt_LOOP_$loopIdx"), INT_TYPE)

        val anonymizingState =
            SymbolicExecutionState(
                emptySet(),
                createAnonymizingLoopStore(writtenVars, loopCntVar, initState, resultState, "_ANON_LOOP_$loopIdx")
            )

        val allRelevantLoopVariables =
            writtenVars.map { variablesRelevantFor(it, initState, resultState) }.flatten()

        val iterationNumberFunctionApp = FunctionApplication(
            FunctionSymbol(
                newNamesCreator.newName("iterations_LOOP_$loopIdx"),
                INT_TYPE,
                allRelevantLoopVariables.map { it.type }
            ),
            allRelevantLoopVariables
        )

        val anonymizingFinalState =
            SymbolicExecutionState(
                emptySet(),
                createAnonymizingLoopStore(
                    writtenVars, iterationNumberFunctionApp, initState, resultState, "_AFTER_LOOP_$loopIdx"
                )
            )

        // Set SESs for loop head and propagate to its non-loop successors
        //stmtToInputSESMap[loop.head] = listOf(anonymizingState.apply(initState).simplify())
        stmtToOutputSESMap[loop.head] =
            loopAnalysis.getOutputSESs(loop.head).map { it.apply(anonymizingFinalState).apply(initState).simplify() }
        propagateResultStateToSuccs(
            loop.head,
            stmtToOutputSESMap[loop.head]?.subList(1, stmtToOutputSESMap[loop.head]?.size ?: 0) ?: emptyList(),
            loop.loopStatements
        )

        // Save loop leaf state, to be able to later check correctness
        // of substituted invariants
        loopLeafSESMap[loop] = SymbolicExecutionState.merge(
            loopAnalysis.stmtToInputSESMap[loop.head]?.subList(
                1,
                loopAnalysis.stmtToOutputSESMap[loop.head]?.size ?: 0
            ) ?: emptyList()
        ).apply(anonymizingState.apply(initState)).simplify()


        for (stmt in loop.loopStatements.filterNot(loop.head::equals)) {
            stmtToInputSESMap[stmt] =
                loopAnalysis.getInputSESs(stmt).map { it.apply(anonymizingState).apply(initState).simplify() }

            if (loop.loopExits.contains(stmt)) {
                // For loop exits: Use parametrized function term for anonymization
                // instead of loop iteration counter variable, and propagate results
                // to successors.

                stmtToOutputSESMap[stmt] =
                    loopAnalysis.getOutputSESs(stmt).map { it.apply(anonymizingFinalState).apply(initState).simplify() }

                propagateResultStateToSuccs(
                    stmt,
                    stmtToOutputSESMap[stmt] ?: emptyList(),
                    loop.loopStatements
                )
            } else {
                // For non-exit loop statements, use the artificial loop counter variable
                // for anonymization.

                stmtToOutputSESMap[stmt] =
                    loopAnalysis.getOutputSESs(stmt).map { it.apply(anonymizingState).apply(initState).simplify() }
            }
        }

        logger.trace("Done analyzing loop ${loopIdx + 1}")
    }

    private fun createAnonymizingLoopStore(
        writtenVars: List<LocalVariable>,
        iterationCounter: SymbolicExpression,
        initState: SymbolicExecutionState,
        resultState: SymbolicExecutionState,
        nameSuffix: String
    ) = writtenVars.associateWith { writtenVar ->
        val relVars = variablesRelevantFor(writtenVar, initState, resultState)
        FunctionApplication(
            FunctionSymbol(
                newNamesCreator.newName(writtenVar.name + nameSuffix),
                writtenVar.type,
                listOf(listOf(iterationCounter.type()), relVars.map { it.type }).flatten()
            ),
            listOf(listOf(iterationCounter), relVars).flatten()
        )
    }.map { ElementaryStore(it.key, it.value) }
        .fold(EmptyStore as SymbolicStore,
            { acc, elem -> ParallelStore.create(acc, elem) })

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