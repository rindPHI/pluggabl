package de.dominicsteinhoefel.pluggabl.analysis

import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.theories.IntTheory.INT_TYPE
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import soot.Body
import soot.Local
import soot.jimple.GotoStmt
import soot.jimple.IfStmt
import soot.jimple.Stmt
import soot.toolkits.graph.UnitGraph
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashSet

class LoopAnalysis(
    private val body: Body,
    private val cfg: UnitGraph,
    private val loop: Loop,
    private val loopIdx: Int,
    private val createSubAnalysis:
        (root: Stmt, stopAtNodes: List<Stmt>, ignoreTopLoop: Boolean) -> SymbolicExecutionAnalysis,
    loopHeadInputStates: List<SymbolicExecutionState>,
    private val symbolsManager: SymbolsManager,
    private val exprConverter: ExprConverter
) {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(LoopAnalysis::class.simpleName)
    }

    private val loopExits = loop.loopExits

    private val initState = SymbolicExecutionState.merge(loopHeadInputStates)
    private val stmtToInputSESMap = HashMap<Stmt, List<SymbolicExecutionState>>()
    private val stmtToOutputSESMap = HashMap<Stmt, List<SymbolicExecutionState>>()

    private lateinit var loopLeafState: SymbolicExecutionState

    fun getInputSESs(node: Stmt) = stmtToInputSESMap[node] ?: emptyList()
    fun getOutputSESs(node: Stmt) = stmtToOutputSESMap[node] ?: emptyList()
    fun getLoopLeafState() = loopLeafState

    fun executeLoopAndAnonymize() {
        val stmtsAfterExit = loopExits.map {
            if (it is GotoStmt) it.target else {
                if (it is IfStmt) it.target else null
            }
        }.filterNot { it == null }.filterNot { loop.loopStatements.contains(it) }.map { it as Stmt }

        val loopAnalysis = createSubAnalysis(
            loop.head,
            stmtsAfterExit,
            true
        )

        loopAnalysis.symbolicallyExecute()

        // Get state upon loop re-entry
        val loopBackjumpState =
            SymbolicExecutionState.merge(
                loop.backJumpStatements.map { loopAnalysis.getInputSESs(it) }.flatten()
            ).simplify().also { println("Merged & simplified loop result state: $it") }

        anonymizeWrittenLoopVars(initState, loopBackjumpState, loopAnalysis)
    }

    private fun anonymizeWrittenLoopVars(
        initState: SymbolicExecutionState,
        loopBackjumpState: SymbolicExecutionState,
        loopAnalysis: SymbolicExecutionAnalysis
    ) {
        val writtenVars = writtenVars()
        val anonymizingState = createAnonymizingState(writtenVars, initState, loopBackjumpState)

        // Save loop leaf state, to be able to later check correctness of substituted invariants
        loopLeafState = loopBackjumpState.apply(anonymizingState.apply(initState)).simplify()
        storeInputAndOutputStatesForLoopStatements(loopAnalysis, initState, anonymizingState)
        storeOutputStatesForLoopExits(
            writtenVars,
            initState,
            loopBackjumpState,
            loopAnalysis,
            anonymizingState
        )
    }

    private fun storeInputAndOutputStatesForLoopStatements(
        loopAnalysis: SymbolicExecutionAnalysis,
        initState: SymbolicExecutionState,
        anonymizingState: SymbolicExecutionState
    ) {
        for (stmt in loop.loopStatements) {
            stmtToInputSESMap[stmt] =
                loopAnalysis.getInputSESs(stmt).map { it.apply(anonymizingState).apply(initState).simplify() }
        }

        for (stmt in loop.loopStatements.filterNot(loopExits::contains)) {
            // For non-exit loop statements, use the artificial loop counter variable for anonymization.
            stmtToOutputSESMap[stmt] =
                loopAnalysis.getOutputSESs(stmt).map { it.apply(anonymizingState).apply(initState).simplify() }
        }
    }

    private fun storeOutputStatesForLoopExits(
        writtenVars: List<LocalVariable>,
        initState: SymbolicExecutionState,
        loopBackjumpState: SymbolicExecutionState,
        loopAnalysis: SymbolicExecutionAnalysis,
        anonymizingState: SymbolicExecutionState
    ) {
        val anonymizingFinalState =
            createAnonymizingFinalState(writtenVars, initState, loopBackjumpState)

        for (stmt in loop.loopStatements.filter(loopExits::contains)) {
            val outputSESs = LinkedList<SymbolicExecutionState>()
            for ((idx, succ) in loopAnalysis.cfg.getSuccsOf(stmt).withIndex()) {
                val anonState =
                    if (loop.loopStatements.contains(succ)) anonymizingState
                    else anonymizingFinalState

                outputSESs.add(loopAnalysis.getOutputSESs(stmt).get(idx).apply(anonState).apply(initState).simplify())
            }

            stmtToOutputSESMap[stmt] = outputSESs
        }
    }

    private lateinit var anonymizingState: SymbolicExecutionState
    private fun createAnonymizingState(
        writtenVars: List<LocalVariable>,
        initState: SymbolicExecutionState,
        loopBackjumpState: SymbolicExecutionState
    ) = if (this::anonymizingState.isInitialized) anonymizingState else
        SymbolicExecutionState(
            emptySet(),
            createAnonymizingLoopStore(
                writtenVars,
                symbolsManager.newLocalVariable("itCnt_LOOP_$loopIdx", INT_TYPE),
                initState,
                loopBackjumpState,
                "_ANON_LOOP_$loopIdx"
            )
        ).also { anonymizingState = it }

    private lateinit var anonymizingFinalState: SymbolicExecutionState
    private fun createAnonymizingFinalState(
        writtenVars: List<LocalVariable>,
        initState: SymbolicExecutionState,
        loopBackjumpState: SymbolicExecutionState
    ) = if (this::anonymizingFinalState.isInitialized) anonymizingFinalState else
        SymbolicExecutionState(
            emptySet(),
            createAnonymizingLoopStore(
                writtenVars,
                iterationNumberFunctionApp(initState, loopBackjumpState),
                initState,
                loopBackjumpState,
                "_AFTER_LOOP_$loopIdx"
            )
        ).also { anonymizingFinalState = it }


    private fun iterationNumberFunctionApp(
        initState: SymbolicExecutionState,
        loopBackjumpState: SymbolicExecutionState
    ): FunctionApplication {
        val terminationRelevantVariables =
            retainVarsOfInitState(
                initState,
                loopBackjumpState.constraints.map { it.accept(LocalVariableConstraintCollector()) }.flatten().toSet()
            )

        val f = symbolsManager.newFunctionSymbol(
            "iterations_LOOP_$loopIdx",
            INT_TYPE,
            terminationRelevantVariables.map { it.type }
        )

        return FunctionApplication(f, terminationRelevantVariables.toList())
    }

    private fun writtenVars() = loop.loopStatements.asSequence().map { it.defBoxes }.flatten().map { it.value }
        .map {
            if (it !is Local) {
                throw NotImplementedError("Anonymization of written heap locations currently not implemented")
            }
            it
        }
        .map { exprConverter.convert(it) as LocalVariable }.toSet().toList()

    private fun createAnonymizingLoopStore(
        writtenVars: List<LocalVariable>,
        iterationCounter: SymbolicExpression,
        initState: SymbolicExecutionState,
        resultState: SymbolicExecutionState,
        nameSuffix: String
    ) = writtenVars.associateWith { writtenVar ->
        logger.debug("Creating anonymizing store for $writtenVar")
        logger.debug("Init state:   $initState")
        logger.debug("Result state: $resultState")

        val relVars = variablesRelevantFor(writtenVar, initState, resultState)
        logger.debug("Rel. vars: ${relVars.joinToString(",")}")

        FunctionApplication(
            symbolsManager.newFunctionSymbol(
                writtenVar.name + nameSuffix,
                writtenVar.type,
                listOf(listOf(iterationCounter.type()), relVars.map { it.type }).flatten()
            ),
            listOf(listOf(iterationCounter), relVars).flatten()
        ).also { logger.debug("Result func sym: $it") }
    }.map { ElementaryStore(it.key, it.value) }
        .let { ParallelStore.create(it) }

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
        return retainVarsOfInitState(initState, result)
    }

    /**
     * @return all elements of result that are either contained in initState or method parameters.
     */
    private fun retainVarsOfInitState(
        initState: SymbolicExecutionState,
        result: Set<LocalVariable>
    ) = result.filter {
        listOf(
            initState.constraints.map { it.accept(LocalVariableConstraintCollector()) }.flatten(),
            initState.store.accept(LocalVariableStoreCollector()),
            body.parameterLocals.map { exprConverter.convert(it) as LocalVariable }
        ).flatten().contains(it)
    }.toSet()

}