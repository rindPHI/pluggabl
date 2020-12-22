package de.dominicsteinhoefel.pluggabl.analysis

import de.dominicsteinhoefel.pluggabl.analysis.rules.SERule
import de.dominicsteinhoefel.pluggabl.expr.LocalVariable
import de.dominicsteinhoefel.pluggabl.expr.SymbolicExecutionState
import de.dominicsteinhoefel.pluggabl.expr.TypeConverter
import de.dominicsteinhoefel.pluggabl.expr.converters.ConstrConverter
import de.dominicsteinhoefel.pluggabl.expr.converters.ExprConverter
import de.dominicsteinhoefel.pluggabl.rules.*
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory.HEAP_VAR
import de.dominicsteinhoefel.pluggabl.theories.IntTheory
import de.dominicsteinhoefel.pluggabl.theories.LocationSetTheory
import de.dominicsteinhoefel.pluggabl.theories.StringTheory
import de.dominicsteinhoefel.pluggabl.util.SootBridge
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import soot.Body
import soot.G
import soot.jimple.Stmt
import soot.jimple.internal.JReturnStmt
import soot.jimple.internal.JimpleLocal
import soot.jimple.toolkits.annotation.logic.LoopFinder
import soot.toolkits.graph.ExceptionalUnitGraph
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap

class SymbolicExecutionAnalysis private constructor(
    val body: Body,
    private val root: Stmt = body.units.first as Stmt,
    private val stopAtNodes: List<Stmt> = emptyList(),
    private val ignoreTopLoop: Boolean = false,
    val typeConverter: TypeConverter = TypeConverter(THEORIES),
    val symbolsManager: SymbolsManager = SymbolsManager(typeConverter),
    val exprConverter: ExprConverter = ExprConverter(symbolsManager, THEORIES),
    val constrConverter: ConstrConverter = ConstrConverter(exprConverter)
) {
    val cfg: ExceptionalUnitGraph = ExceptionalUnitGraph(body)

    private val stmtToInputSESMap = HashMap<Stmt, List<SymbolicExecutionState>>()
    private val stmtToOutputSESMap = HashMap<Stmt, List<SymbolicExecutionState>>()
    private val loopLeafSESMap = LinkedHashMap<Loop, SymbolicExecutionState>()

    private val loops = LoopFinder().getLoops(body).map { Loop.fromSootLoop(it, cfg) }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SymbolicExecutionAnalysis::class.simpleName)

        private val RULES = arrayOf(
            AssignSimpleRule,
            AssignFromFieldRule,
            AssignToFieldRule,
            AssignFromArrayRule,
            AssignToArrayRule,
            AssignFromPureInvokationRule,
            AssignFromImpureInvokationRule,
            AssignArrayLengthRule,
            ImpureInvokationRule,
            PureInvokationRule,
            ReturnValueRule,
            ReturnVoidRule,
            IfRule,
            DummyRule
        )

        private val THEORIES = setOf(HeapTheory, IntTheory, StringTheory)

        fun create(clazz: String, methodSig: String, sootClassPathElems: List<String>): SymbolicExecutionAnalysis {
            G.reset()
            return SymbolicExecutionAnalysis(
                SootBridge.loadJimpleBody(clazz, methodSig, sootClassPathElems)
                    ?: throw IllegalStateException("Could not load method $methodSig of class $clazz")
            ).also { it.registerSymbols() }
        }

        fun create(
            body: Body,
            root: Stmt = body.units.first as Stmt,
            forceLeaves: List<Stmt> = emptyList(),
            ignoreTopLoop: Boolean = false
        ): SymbolicExecutionAnalysis {
            G.reset()
            return SymbolicExecutionAnalysis(body, root, forceLeaves, ignoreTopLoop)
                .also { it.registerSymbols() }
        }

        // Would be nice to do a purity analysis of the JRE library classes and store results...
        private lateinit var pureMethods: Set<String>
        private const val PURE_METHODS_LIST_FILE = "/de/dominicsteinhoefel/pluggabl/pureMethods.txt"

        private fun getPureMethods(): Set<String> {
            if (!this::pureMethods.isInitialized) {
                pureMethods =
                    SymbolicExecutionAnalysis::class.java.getResource(PURE_METHODS_LIST_FILE)
                        .readText().split("\n")
                        .asSequence()
                        .map { it.trim() }
                        .filterNot { it.isBlank() }
                        .filterNot { it.startsWith("#") }
                        .map { "<$it>" }.toSet()
            }

            return pureMethods
        }

        fun isPureMethod(methodSig: String) = getPureMethods().contains(methodSig)
    }

    private fun createSubAnalysis(
        root: Stmt,
        stopAtNodes: List<Stmt>,
        ignoreTopLoop: Boolean
    ) = SymbolicExecutionAnalysis(
        body,
        root,
        stopAtNodes,
        ignoreTopLoop,
        typeConverter,
        symbolsManager,
        exprConverter,
        constrConverter
    )

    fun getInputSESs(node: Stmt) = stmtToInputSESMap[node] ?: emptyList()
    fun getOutputSESs(node: Stmt) = stmtToOutputSESMap[node] ?: emptyList()
    fun getLoopLeafSESs() = loopLeafSESMap.toMap()
    fun getLeavesWithOutputSESs() = cfg.tails.map { it as Stmt }.associateWith { getOutputSESs(it) }

    fun getLocal(varName: String): LocalVariable =
        body.locals.firstOrNull { it.name == varName }
            ?.let { it as JimpleLocal }
            ?.let { symbolsManager.localVariableFor(it) }
            ?: symbolsManager.getLocalVariables().first { it.name == varName }

    fun getFunctionSymbol(funcName: String) =
        symbolsManager.getFunctionSymbols().first { it.name == funcName }

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

            val loop = loops.firstOrNull { it.head == currStmt }

            val stmtIsHeadOfAnalyzedLoop = ignoreTopLoop && loop?.head == root

            if (loop != null && !stmtIsHeadOfAnalyzedLoop) {
                analyzeLoop(loop)

                queue.addAll(loop.loopExits.map { cfg.getUnexceptionalSuccsOf(it) }.flatten().map { it as Stmt }
                    .filterNot(loop.loopStatements::contains))

                continue
            }

            val inputStates = stmtToInputSESMap[currStmt] ?: emptyList()

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
            val result = rule.apply(
                currStmt,
                inputStates,
                symbolsManager,
                typeConverter,
                exprConverter,
                constrConverter
            ).map { it.simplify() }

            if (result.size != cfg.getUnexceptionalSuccsOf(currStmt).size && currStmt !is JReturnStmt) {
                throw IllegalStateException(
                    "Expected number ${result.size} of successors in SE graph " +
                            "does not match number of successors in CFG (${cfg.getUnexceptionalSuccsOf(currStmt).size})"
                )
            }

            stmtToOutputSESMap[currStmt] = result

            if (loops.none { it.backJumpStatements.contains(currStmt) }) {
                propagateResultStateToSuccs(currStmt, result)
                cfg.getUnexceptionalSuccsOf(currStmt).map { it as Stmt }.forEach(queue::addLastDistinct)
            }
        }
    }

    private fun registerSymbols() {
        symbolsManager.registerHeapVar(HEAP_VAR)
        body.method.returnType.let {
            if (it != G.v().soot_VoidType()) {
                symbolsManager.registerResultVar(LocalVariable("result", typeConverter.convert(it)))
            }
        }

        body.locals.forEach { symbolsManager.registerJimpleLocal(it as JimpleLocal) }
        body.parameterLocals.forEach { symbolsManager.registerJimpleLocal(it as JimpleLocal) }

        symbolsManager.registerFunctionSymbols(HeapTheory.functions())
        symbolsManager.registerFunctionSymbols(LocationSetTheory.functions())
    }

    private fun analyzeLoop(loop: Loop) {
        if (loop.loopExits.isEmpty()) {
            throw IllegalStateException("Endless loops (w/o exists) are currently not supported.")
        }

        val loopIdx = loops.indexOf(loop)
        logger.trace("Analyzing loop ${loopIdx + 1}")

        val loopAnalysis = LoopAnalysis(
            body,
            loop,
            loopIdx,
            this::createSubAnalysis,
            stmtToInputSESMap[loop.head] ?: emptyList(),
            symbolsManager,
            exprConverter
        )

        loopAnalysis.executeLoopAndAnonymize()

        loopAnalysis.getLoopLeafState().let { loopLeafSESMap[loop] = it }

        loop.loopStatements.forEach { loopStmt ->
            if (loopStmt != loop.head) stmtToInputSESMap[loopStmt] = loopAnalysis.getInputSESs(loopStmt)
            stmtToOutputSESMap[loopStmt] = loopAnalysis.getOutputSESs(loopStmt)
        }

        loop.loopExits.forEach { loopExitStmt ->
            for ((idx, succ) in cfg.getUnexceptionalSuccsOf(loopExitStmt).map { it as Stmt }.withIndex()) {
                if (loop.loopStatements.contains(succ)) continue
                stmtToInputSESMap[succ] = listOf(
                    stmtToInputSESMap[succ].orEmpty(),
                    stmtToOutputSESMap[loopExitStmt]?.get(idx)
                        .let { if (it == null) emptyList() else listOf(it) }).flatten()
            }
        }

        logger.trace("Done analyzing loop ${loopIdx + 1}")
    }

    private fun propagateResultStateToSuccs(
        currStmt: Stmt,
        result: List<SymbolicExecutionState>
    ) {
        cfg.getUnexceptionalSuccsOf(currStmt).map { it as Stmt }
            .zip(result).forEach { (cfgNode, ses) ->
                stmtToInputSESMap[cfgNode] =
                    listOf(
                        stmtToInputSESMap[cfgNode] ?: emptyList(),
                        listOf(ses)
                    ).flatten()
            }
    }

    private fun getApplicableRule(
        currStmt: Stmt,
        inputStates: List<SymbolicExecutionState>
    ): SERule {
        val applicableRules = RULES.filter { it.accepts(currStmt, inputStates) }
        logger.debug(
            "Applicable rules for statement type ${currStmt::class.simpleName}, " +
                    "${inputStates.size} input states: " +
                    applicableRules.joinToString(", ")
        )

        if (applicableRules.size != 1) {
            throw IllegalStateException(
                "${applicableRules.size} rules found for statement \"$currStmt\", expected 1"
            )
        }

        return applicableRules[0]
    }
}