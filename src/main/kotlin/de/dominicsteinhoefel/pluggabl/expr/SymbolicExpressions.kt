package de.dominicsteinhoefel.pluggabl.expr

import de.dominicsteinhoefel.pluggabl.analysis.SymbolsManager
import de.dominicsteinhoefel.pluggabl.theories.Theory
import de.dominicsteinhoefel.pluggabl.util.union
import org.slf4j.LoggerFactory
import soot.jimple.IntConstant
import soot.jimple.internal.*
import java.util.*

interface SymbolicExpressionsVisitor<T> {
    fun visit(e: Value): T
    fun visit(e: LocalVariable): T
    fun visit(e: FunctionApplication): T
    fun visit(e: ConditionalExpression): T
    fun visit(e: StoreApplExpression): T
}

sealed class SymbolicExpression {
    abstract fun type(): Type
    abstract fun <T> accept(visitor: SymbolicExpressionsVisitor<T>): T
}

abstract class Value : SymbolicExpression()

interface BinarySymbolicExpression {
    fun left(): SymbolicExpression
    fun right(): SymbolicExpression
}

class LocalVariable(
    val name: String,
    val type: Type
) : SymbolicExpression() {
    override fun type() = type
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)

    override fun toString() = name
    override fun hashCode() = Objects.hash(LocalVariable::class, name, type)
    override fun equals(other: Any?) = (other as? LocalVariable).let { it?.name == name && it.type == type }
}

open class FunctionSymbol(
    val name: String,
    val type: Type,
    val paramTypes: List<Type>,
    val unique: Boolean = false
) {
    constructor(
        name: String,
        type: Type,
        vararg paramTypes: Type,
        unique: Boolean = false
    ) : this(name, type, paramTypes.toList(), unique)

    override fun toString() = "$type $name(${paramTypes.joinToString(", ")})"
    override fun hashCode() = Objects.hash(FunctionSymbol::class, name, type, paramTypes)
    override fun equals(other: Any?) =
        (other as? FunctionSymbol).let { it?.name == name && it.type == type && it.paramTypes == paramTypes }
}

class FunctionApplication(
    val f: FunctionSymbol,
    val args: List<SymbolicExpression>
) : SymbolicExpression() {
    constructor(
        f: FunctionSymbol,
        vararg args: SymbolicExpression
    ) : this(f, args.toList())

    override fun type() = f.type
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)

    override fun toString() = "${f.name}${args.joinToString(", ").let { if (it.isBlank()) "" else "($it)" }}"
    override fun hashCode() = Objects.hash(FunctionApplication::class, f, args)
    override fun equals(other: Any?) = (other as? FunctionApplication).let { it?.f == f && it.args == args }
}

class StoreApplExpression private constructor(val applied: SymbolicStore, val target: SymbolicExpression) :
    SymbolicExpression() {
    override fun type() = target.type()
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)

    override fun toString() = "{${applied}}(${target})"
    override fun hashCode() = Objects.hash(StoreApplExpression::class, applied, target)
    override fun equals(other: Any?) =
        (other as? StoreApplExpression).let { it?.applied == applied && it.target == target }

    companion object {
        fun create(applied: SymbolicStore, target: SymbolicExpression) =
            if (applied is EmptyStore) target else
                StoreApplExpression(applied, target)
    }
}

class ConditionalExpression private constructor(
    val condition: SymbolicConstraint,
    val vThen: SymbolicExpression,
    val vElse: SymbolicExpression
) : SymbolicExpression() {
    override fun type() = vThen.type() // TODO: Have to take common supertype
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)

    override fun toString() = "if (${condition}) then (${vThen}) else (${vElse})"
    override fun hashCode() = Objects.hash(ConditionalExpression::class, condition, vThen, vElse)
    override fun equals(other: Any?) =
        (other as? ConditionalExpression).let { it?.condition == condition && it.vThen == vThen && it.vElse == vElse }

    companion object {
        fun create(
            condition: SymbolicConstraint,
            vThen: SymbolicExpression,
            vElse: SymbolicExpression
        ): SymbolicExpression =
            when (condition) {
                is True -> vThen
                is False -> vElse
                is NegatedConstr -> create(condition.inner(), vElse, vThen)
                else ->
                    if (vThen == vElse) vThen else
                        ConditionalExpression(condition, vThen, vElse)
            }
    }
}

class ExprConverter(private val symbolsManager: SymbolsManager, private val theories: Set<Theory>) {
    private val logger = LoggerFactory.getLogger(ExprConverter::class.simpleName)

    companion object {
        /**
         * Simple expressions are constant values, local variables, or
         * composed arithmetic expressions of simple expressions. The latter
         * case is possible since field accesses are decomposed by the
         * conversion to Jimple; in the KeY system, for instance, more
         * complex decomposition rules are necessary and simple expressions
         * are only constants or variables.
         */
        fun isSimpleExpression(value: soot.Value): Boolean =
            when (value) {
                is JimpleLocal, is IntConstant, is JAddExpr, is JSubExpr, is JMulExpr -> true
                else -> false
            }
    }

    fun convert(value: soot.Value): SymbolicExpression =
        when (value) {
            is JimpleLocal -> symbolsManager.localVariableFor(value)
            is JVirtualInvokeExpr -> {
                FunctionApplication(
                    symbolsManager.getMethodResultSymbol(value.methodRef),
                    listOf(listOf(convert(value.base)),
                        value.args.map { convert(it) }).flatten()
                )
            }
            is JStaticInvokeExpr -> {
                FunctionApplication(
                    symbolsManager.getMethodResultSymbol(value.methodRef),
                    value.args.map { convert(it) }.toList()
                )
            }
            is soot.Value -> theories.filter { it.isResponsibleFor(value) }
                .also { if (it.size != 1) throw IllegalArgumentException("No theory found for operator ${value::class}") }[0]
                .translate(value, value.useBoxes.map { it.value }.map { convert(it) })
            else -> TODO("Conversion of type ${value.javaClass} to SymbolicExpression not yet implemented.")
        }
}

open class ExpressionCollector<T>(private val coll: (SymbolicExpression) -> Set<T>) :
    SymbolicExpressionsVisitor<Set<T>> {
    override fun visit(e: Value) = coll(e)
    override fun visit(e: LocalVariable) = coll(e)
    override fun visit(e: FunctionApplication) =
        union(coll(e), e.args.map { it.accept(this) }.flatten().toSet())

    override fun visit(e: StoreApplExpression) =
        union(coll(e), e.target.accept(this))

    override fun visit(e: ConditionalExpression) =
        union(
            coll(e),
            e.vThen.accept(this),
            e.vElse.accept(this)
        )
}

class LocalVariableExpressionCollector() : ExpressionCollector<LocalVariable>({ e ->
    when (e) {
        is LocalVariable -> setOf(e)
        is StoreApplExpression -> e.applied.accept(LocalVariableStoreCollector())
        is ConditionalExpression -> e.condition.accept(LocalVariableConstraintCollector())
        else -> emptySet()
    }
})

class FunctionSymbolExpressionCollector() : ExpressionCollector<FunctionSymbol>({ e ->
    when (e) {
        is FunctionApplication -> setOf(e.f)
        is StoreApplExpression -> e.applied.accept(FunctionSymbolStoreCollector())
        is ConditionalExpression -> e.condition.accept(FunctionSymbolConstraintCollector())
        else -> emptySet()
    }
})

open class ExpressionReplacer(private val repl: (SymbolicExpression) -> SymbolicExpression) :
    SymbolicExpressionsVisitor<SymbolicExpression> {
    override fun visit(e: Value) = repl(e)
    override fun visit(e: LocalVariable) = repl(e)
    override fun visit(e: FunctionApplication) = repl(FunctionApplication(e.f, e.args.map { it.accept(this) }))

    override fun visit(e: StoreApplExpression) =
        repl(StoreApplExpression.create(e.applied, e.target.accept(this)))

    override fun visit(e: ConditionalExpression) =
        repl(
            ConditionalExpression.create(
                e.condition,
                e.vThen.accept(this),
                e.vElse.accept(this)
            )
        )

}

class SymbolReplaceExprVisitor(val replMap: Map<LocalVariable, SymbolicExpression>) : ExpressionReplacer({ e ->
    when (e) {
        is LocalVariable -> replMap[e] ?: e
        is ConditionalExpression -> ConditionalExpression.create(
            e.condition.accept(SymbolReplaceConstrVisitor(replMap)),
            e.vThen,
            e.vElse
        )
        is StoreApplExpression ->
            throw UnsupportedOperationException("Symbol replacement below the scope of store application unsupported, normalize first")
        else -> e
    }
})