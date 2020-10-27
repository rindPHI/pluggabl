package de.dominicsteinhoefel.symbex.expr

import soot.jimple.IntConstant
import soot.jimple.internal.JAddExpr
import soot.jimple.internal.JimpleLocal
import soot.IntType

data class Type(val type: String)

val INT_TYPE = Type("int")

object TypeConverter {
    fun convert(type: soot.Type) =
        when (type) {
            is IntType -> INT_TYPE
            else -> TODO("Conversion of type ${type} not yet implemented.")
        }
}

interface SymbolicExpressionsVisitor<T> {
    fun visit(e: IntValue): T
    fun visit(e: Symbol): T
    fun visit(e: StoreApplExpression): T
    fun visit(e: ConditionalExpression): T
    fun visit(e: AdditionExpr): T
}

sealed class SymbolicExpression {
    abstract fun type(): Type
    abstract fun <T> accept(visitor: SymbolicExpressionsVisitor<T>): T
}

abstract class Value : SymbolicExpression()

data class IntValue(val value: Int) : Value() {
    override fun type() = INT_TYPE
    override fun toString() = value.toString()
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)
}

interface BinarySymbolicExpression {
    fun left(): SymbolicExpression
    fun right(): SymbolicExpression
}

data class Symbol(
    val name: String,
    val type: Type
) : SymbolicExpression() {
    override fun type() = type
    override fun toString() = name
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)
}

class StoreApplExpression private constructor(val applied: SymbolicStore, val target: SymbolicExpression) :
    SymbolicExpression() {
    override fun type() = target.type()
    override fun toString() = "{${applied}}(${target})"
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)

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
    override fun toString() = "if (${condition}) then (${vThen}) else (${vElse})"
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)

    companion object {
        fun create(
            condition: SymbolicConstraint,
            vThen: SymbolicExpression,
            vElse: SymbolicExpression
        ) =
            when (condition) {
                is True -> vThen
                is False -> vElse
                else ->
                    if (vThen.equals(vElse)) vThen else
                        ConditionalExpression(condition, vThen, vElse)
            }
    }
}

data class AdditionExpr(
    val left: SymbolicExpression,
    val right: SymbolicExpression
) : SymbolicExpression(), BinarySymbolicExpression {
    override fun left() = left
    override fun right() = right
    override fun type() = left.type() // TODO: Have to take common supertype
    override fun toString() = "(${left()})+(${right()})"
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)
}

class SymbolReplaceExprVisitor(val replMap: Map<Symbol, SymbolicExpression>) :
    SymbolicExpressionsVisitor<SymbolicExpression> {
    override fun visit(e: IntValue): SymbolicExpression = e
    override fun visit(e: Symbol): SymbolicExpression = replMap.get(e) ?: e

    override fun visit(e: ConditionalExpression): SymbolicExpression =
        ConditionalExpression.create(
            e.condition.accept(SymbolReplaceConstrVisitor(replMap)),
            e.vThen.accept(this),
            e.vElse.accept(this)
        )

    override fun visit(e: AdditionExpr): SymbolicExpression =
        AdditionExpr(e.left.accept(this), e.right.accept(this))

    override fun visit(e: StoreApplExpression): SymbolicExpression =
        throw UnsupportedOperationException("Symbol replacement below the scope of store application unsupported, normalize first")
}

object ExprConverter {
    fun convert(value: soot.Value): SymbolicExpression =
        when (value) {
            is JimpleLocal -> Symbol(value.getName(), TypeConverter.convert(value.getType()))
            is IntConstant -> IntValue(value.value)
            is JAddExpr -> AdditionExpr(convert(value.getOp1()), convert(value.getOp2()))
            else -> TODO("Conversion of type ${value.getType()} to SymbolicExpression not yet implemented.")
        }
}