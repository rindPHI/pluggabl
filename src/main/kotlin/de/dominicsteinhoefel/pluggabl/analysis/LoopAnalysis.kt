package de.dominicsteinhoefel.pluggabl.analysis

import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.util.NewNamesCreator
import soot.Body
import soot.Local
import soot.jimple.GotoStmt
import soot.jimple.IfStmt
import soot.jimple.Stmt
import soot.jimple.toolkits.annotation.logic.Loop

class LoopAnalysis(
    private val body: Body,
    private val loop: Loop,
    private val loopIdx: Int,
    private val stmtToInputSESMap: HashMap<Stmt, List<SymbolicExecutionState>>,
    private val stmtToOutputSESMap: HashMap<Stmt, List<SymbolicExecutionState>>,
    private val loopLeafSESMap: LinkedHashMap<Loop, SymbolicExecutionState>,
    private val propagateResultStateToSuccs: (
        currStmt: Stmt,
        result: List<SymbolicExecutionState>,
        ignore: List<Stmt>
    ) -> Unit
) {
    private val newNamesCreator = NewNamesCreator()

    fun executeLoopAndAnonymize() {
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

        val initState = SymbolicExecutionState.merge(stmtToInputSESMap[loop.head] ?: emptyList())

        // merge all states of all loop exits
        val loopExitsInputState = SymbolicExecutionState.merge(
            loopAnalysis.stmtToInputSESMap.filter { loop.loopExits.contains(it.key) }
                .map { it.value }.flatten().filterNot(SymbolicExecutionState::isEmpty)
        )

        val loopHeadInputState =
            SymbolicExecutionState.merge(
                loopAnalysis.stmtToInputSESMap[loop.head]?.subList(1) ?: emptyList()
            ).simplify()

        anonymizeWrittenLoopVars(initState, loopExitsInputState, loopHeadInputState, loopAnalysis)
        propagateResultsToAfterLoop()
    }

    private fun anonymizeWrittenLoopVars(
        initState: SymbolicExecutionState,
        loopExitsInputState: SymbolicExecutionState,
        loopHeadInputState: SymbolicExecutionState,
        loopAnalysis: SymbolicExecutionAnalysis
    ) {
        val writtenVars = writtenVars()
        val anonymizingState = createAnonymizingState(writtenVars, initState, loopExitsInputState)

        storeLoopLeafState(loopHeadInputState, anonymizingState, initState)
        storeInputAndOutputStatesForLoopStatements(loopAnalysis, initState, anonymizingState)
        storeOutputStatesForLoopExits(writtenVars, initState, loopHeadInputState, loopExitsInputState, loopAnalysis)
    }

    private fun propagateResultsToAfterLoop() {
        propagateResultStateToSuccs(
            loop.head,
            stmtToOutputSESMap[loop.head]?.subList(1) ?: emptyList(),
            loop.loopStatements
        )

        for (stmt in loop.loopStatements.filterNot(loop.head::equals).filter(loop.loopExits::contains)) {
            propagateResultStateToSuccs(
                stmt,
                stmtToOutputSESMap[stmt] ?: emptyList(),
                loop.loopStatements
            )
        }
    }

    private fun createAnonymizingState(
        writtenVars: List<LocalVariable>,
        initState: SymbolicExecutionState,
        loopExitsInputState: SymbolicExecutionState
    ) = SymbolicExecutionState(
        emptySet(),
        createAnonymizingLoopStore(
            writtenVars,
            LocalVariable(newNamesCreator.newName("itCnt_LOOP_$loopIdx"), INT_TYPE),
            initState,
            loopExitsInputState,
            "_ANON_LOOP_$loopIdx"
        )
    )

    private fun createAnonymizingFinalState(
        writtenVars: List<LocalVariable>,
        initState: SymbolicExecutionState,
        loopHeadInputState: SymbolicExecutionState,
        loopExitsInputState: SymbolicExecutionState
    ): SymbolicExecutionState {
        val anonymizingFinalState =
            SymbolicExecutionState(
                emptySet(),
                createAnonymizingLoopStore(
                    writtenVars,
                    iterationNumberFunctionApp(initState, loopHeadInputState),
                    initState,
                    loopExitsInputState,
                    "_AFTER_LOOP_$loopIdx"
                )
            )
        return anonymizingFinalState
    }

    private fun storeLoopLeafState(
        loopHeadInputState: SymbolicExecutionState,
        anonymizingState: SymbolicExecutionState,
        initState: SymbolicExecutionState
    ) {
        // Save loop leaf state, to be able to later check correctness of substituted invariants
        loopLeafSESMap[loop] = loopHeadInputState.apply(anonymizingState.apply(initState)).simplify()
    }

    private fun storeOutputStatesForLoopExits(
        writtenVars: List<LocalVariable>,
        initState: SymbolicExecutionState,
        loopHeadInputState: SymbolicExecutionState,
        loopExitsInputState: SymbolicExecutionState,
        loopAnalysis: SymbolicExecutionAnalysis
    ) {
        val anonymizingFinalState =
            createAnonymizingFinalState(writtenVars, initState, loopHeadInputState, loopExitsInputState)

        // Set SESs for loop head and propagate to its non-loop successors
        stmtToOutputSESMap[loop.head] =
            loopAnalysis.getOutputSESs(loop.head).map { it.apply(anonymizingFinalState).apply(initState).simplify() }

        for (stmt in loop.loopStatements.filterNot(loop.head::equals).filter(loop.loopExits::contains)) {
            // For loop exits: Use parametrized function term for anonymization
            // instead of loop iteration counter variable, and propagate results
            // to successors.
            stmtToOutputSESMap[stmt] =
                loopAnalysis.getOutputSESs(stmt).map { it.apply(anonymizingFinalState).apply(initState).simplify() }
        }
    }

    private fun storeInputAndOutputStatesForLoopStatements(
        loopAnalysis: SymbolicExecutionAnalysis,
        initState: SymbolicExecutionState,
        anonymizingState: SymbolicExecutionState
    ) {
        for (stmt in loop.loopStatements.filterNot(loop.head::equals)) {
            stmtToInputSESMap[stmt] =
                loopAnalysis.getInputSESs(stmt).map { it.apply(anonymizingState).apply(initState).simplify() }
        }

        for (stmt in loop.loopStatements.filterNot(loop.head::equals).filterNot(loop.loopExits::contains)) {
            // For non-exit loop statements, use the artificial loop counter variable for anonymization.
            stmtToOutputSESMap[stmt] =
                loopAnalysis.getOutputSESs(stmt).map { it.apply(anonymizingState).apply(initState).simplify() }
        }
    }

    private fun iterationNumberFunctionApp(
        initState: SymbolicExecutionState,
        loopHeadInputState: SymbolicExecutionState
    ): FunctionApplication {
        val terminationRelevantVariables =
            retainVarsOfInitState(
                initState,
                loopHeadInputState.constraints.map { it.accept(LocalVariableConstraintCollector()) }.flatten().toSet()
            )

        return FunctionApplication(
            FunctionSymbol(
                newNamesCreator.newName("iterations_LOOP_$loopIdx"),
                INT_TYPE,
                terminationRelevantVariables.map { it.type }
            ),
            terminationRelevantVariables.toList()
        )
    }

    private fun writtenVars() = loop.loopStatements.asSequence().map { it.defBoxes }.flatten().map { it.value }
        .map {
            if (it !is Local) {
                throw NotImplementedError("Anonymization of written heap locations currently not implemented")
            }
            it
        }
        .map { ExprConverter.convert(it) as LocalVariable }.toSet().toList()

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
        .fold(
            EmptyStore as SymbolicStore,
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
            body.parameterLocals.map { ExprConverter.convert(it) as LocalVariable }
        ).flatten().contains(it)
    }.toSet()

}

fun <E> List<E>.subList(from: Int) = this.subList(from, this.size)