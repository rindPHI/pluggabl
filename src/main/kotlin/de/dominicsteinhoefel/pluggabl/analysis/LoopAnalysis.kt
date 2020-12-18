package de.dominicsteinhoefel.pluggabl.analysis

import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.expr.converters.ExprConverter
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory
import de.dominicsteinhoefel.pluggabl.theories.IntTheory.INT_TYPE
import de.dominicsteinhoefel.pluggabl.util.union
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import soot.Body
import soot.jimple.GotoStmt
import soot.jimple.IfStmt
import soot.jimple.Stmt
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashSet

class LoopAnalysis(
    private val body: Body,
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
            ).simplify().also { logger.debug("Merged & simplified loop result state:\n$it") }

        anonymizeWrittenLoopVars(initState, loopBackjumpState, loopAnalysis)
    }

    private fun anonymizeWrittenLoopVars(
        initState: SymbolicExecutionState,
        loopBackjumpState: SymbolicExecutionState,
        loopAnalysis: SymbolicExecutionAnalysis
    ) {
        val writtenVars = writtenVars(loopBackjumpState)
        // TODO: Maybe add a more fine-grained heap anonymization for actually changed locations.
        //       Could, though, cause reachability problems for dynamically allocated stuff, like
        //       when using iterators.
        // val writtenHeapLocs = writtenHeapLocs(loopBackjumpState)

        val anonymizingState = createAnonymizingState(writtenVars, initState, loopBackjumpState)
        val anonymizingFinalState =
            createAnonymizingFinalState(writtenVars, initState, loopBackjumpState)

        // Save loop leaf state, to be able to later check correctness of substituted invariants
        loopLeafState = loopBackjumpState.apply(anonymizingState.apply(initState)).simplify()
        storeInputAndOutputStatesForLoopStatements(loopAnalysis, initState, anonymizingState)
        storeOutputStatesForLoopExits(
            initState,
            loopAnalysis,
            anonymizingState,
            anonymizingFinalState
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
        initState: SymbolicExecutionState,
        loopAnalysis: SymbolicExecutionAnalysis,
        anonymizingState: SymbolicExecutionState,
        anonymizingFinalState: SymbolicExecutionState
    ) {
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
            retainLocsOfInitState(
                initState,
                loopBackjumpState.constraints.map { it.accept(LocalVariableConstraintCollector()) }.flatten().toSet()
            )

        val f = symbolsManager.newFunctionSymbol(
            "iterations_LOOP_$loopIdx",
            INT_TYPE,
            terminationRelevantVariables.map { it.type() }
        )

        return FunctionApplication(f, terminationRelevantVariables.toList())
    }

    private fun writtenVars(loopBackjumpState: SymbolicExecutionState) =
        loopBackjumpState.store.elementaries().map { it.lhs }

    /**
     * Computes a map from all heap locations written in the passed SES to the symbolic expression
     * stored at that location.
     */
    private fun writtenHeapLocs(loopBackjumpState: SymbolicExecutionState): Map<HeapExpression, SymbolicExpression> =
        loopBackjumpState.store.elementaries().firstOrNull { it.lhs == HeapTheory.HEAP_VAR }?.rhs?.accept(
            ExpressionCollector { e ->
                when (e) {
                    is FunctionApplication ->
                        if (e.f == HeapTheory.STORE)
                            ((e.args[2] as FunctionApplication).f).let { field ->
                                if (field == HeapTheory.ARRAY_FIELD)
                                    setOf(
                                        ArrayRef(
                                            e.args[1],
                                            (e.args[2] as FunctionApplication).args[0]
                                        ) as HeapExpression to e.args[3]
                                    )
                                else
                                    setOf(
                                        FieldRef(
                                            e.args[1],
                                            field as Field
                                        ) as HeapExpression to e.args[3]
                                    )
                            }
                        else emptySet()
                    else -> emptySet()
                }
            })?.toMap() ?: emptyMap()

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

        val relLocs = locationsRelevantFor(writtenVar, initState, resultState)
        logger.debug("Rel. locs: ${relLocs.joinToString(", ")}")

        FunctionApplication(
            symbolsManager.newFunctionSymbol(
                writtenVar.name + nameSuffix,
                writtenVar.type,
                listOf(listOf(iterationCounter.type()), relLocs.map { it.type() }).flatten()
            ),
            listOf(listOf(iterationCounter), relLocs.map { it.toSelectAllExpr() }).flatten()
        ).also { logger.debug("Result func sym: $it") }
    }.map { ElementaryStore(it.key, it.value) }
        .let { ParallelStore.create(it) }

    private fun locationsRelevantFor(
        lv: LocalVariable,
        initState: SymbolicExecutionState,
        finalState: SymbolicExecutionState
    ): Set<Location> {
        val rhsExpression = SymbolicStore.elementaries(finalState.store).first { it.lhs == lv }.rhs

        val result = LinkedHashSet<Location>()
            .also { it.addAll(rhsExpression.accept(LocalVariableExpressionCollector()).filterNot{ lv -> lv == HeapTheory.HEAP_VAR }) }
            .also { it.addAll(rhsExpression.accept(HeapExpressionInExpressionCollector())) }

        while (true) {
            val newLocals = finalState.constraints.map {
                union(
                    it.accept(LocalVariableConstraintCollector()),
                    it.accept(HeapExpressionConstraintCollector())
                )
            }.filter { it.any(result::contains) }.flatten().toSet()

            if (result.containsAll(newLocals)) {
                break
            }

            result.addAll(newLocals)
        }

        // Remove all that are not assigned before and are not method parameters
        return retainLocsOfInitState(initState, result)
    }

    /**
     * @return all elements of result that are either contained in initState or method parameters.
     */
    private fun retainLocsOfInitState(
        initState: SymbolicExecutionState,
        result: Set<Location>
    ) = result.filter { resultLoc ->
        resultLoc !is LocalVariable || listOf(
            initState.constraints.map { it.accept(LocalVariableConstraintCollector()) }.flatten(),
            initState.constraints.map { it.accept(HeapExpressionConstraintCollector()) }.flatten(),
            initState.store.accept(LocalVariableStoreCollector()),
            initState.store.accept(HeapExpressionStoreCollector()),
            body.parameterLocals.map { exprConverter.convert(it) as LocalVariable }
        ).flatten().contains(resultLoc)
    }.toSet()

}