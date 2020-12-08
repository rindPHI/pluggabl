package de.dominicsteinhoefel.pluggabl.expr

import org.slf4j.LoggerFactory
import soot.CharType
import soot.jimple.IntConstant
import soot.IntType
import soot.jimple.internal.*
import java.util.*

open class Type(val type: String) {
    override fun hashCode() = Objects.hash(Type::class, type)
    override fun equals(other: Any?) = (other as? Type)?.type == type
}

val INT_TYPE = Type("int")
val CHAR_TYPE = Type("char")

open class ReferenceType(type: String) : Type(type)
class ArrayType(val baseType: Type) : ReferenceType("[$baseType")

object TypeConverter {
    fun convert(type: soot.Type): Type {
        return when (type) {
            is IntType -> INT_TYPE
            is CharType -> CHAR_TYPE
            is soot.RefType -> ReferenceType(type.className)
            is soot.ArrayType -> ArrayType(convert(type.baseType))
            else -> TODO("Conversion of type $type not yet implemented.")
        }
    }
}

interface SymbolicExpressionsVisitor<T> {
    fun visit(e: IntValue): T
    fun visit(e: LocalVariable): T
    fun visit(e: FunctionApplication): T
    fun visit(e: StoreApplExpression): T
    fun visit(e: ConditionalExpression): T
    fun visit(e: AdditionExpr): T
    fun visit(e: MultiplicationExpr): T
    fun visit(e: LengthExpression): T
    fun visit(e: ArrayReference): T
    fun visit(e: MethodInvocationExpression): T
}

sealed class SymbolicExpression {
    abstract fun type(): Type
    abstract fun <T> accept(visitor: SymbolicExpressionsVisitor<T>): T
}

abstract class Value : SymbolicExpression()

class IntValue(val value: Int) : Value() {
    override fun type() = INT_TYPE
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)

    override fun toString() = value.toString()
    override fun hashCode() = Objects.hash(IntValue::class, value.hashCode())
    override fun equals(other: Any?) = (other as? IntValue)?.value == value
}

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

class FunctionSymbol(
    val name: String,
    val type: Type,
    val paramTypes: List<Type>
) {
    override fun toString() = "$type $name(${paramTypes.joinToString(", ")})"
    override fun hashCode() = Objects.hash(FunctionSymbol::class, name, type, paramTypes)
    override fun equals(other: Any?) =
        (other as? FunctionSymbol).let { it?.name == name && it.type == type && it.paramTypes == paramTypes }
}

class FunctionApplication(
    val f: FunctionSymbol,
    val args: List<SymbolicExpression>
) : SymbolicExpression() {
    override fun type() = f.type
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)

    override fun toString() = "${f.name}(${args.joinToString(", ")})"
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

class AdditionExpr(
    val left: SymbolicExpression,
    val right: SymbolicExpression
) : SymbolicExpression(), BinarySymbolicExpression {
    override fun left() = left
    override fun right() = right
    override fun type() = left.type() // TODO: Have to take common supertype

    override fun toString() = "(${left()})+(${right()})"
    override fun hashCode() = Objects.hash(AdditionExpr::class, left, right)
    override fun equals(other: Any?) =
        (other as? AdditionExpr).let { it?.left == left && it.right == right }

    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)
}

class MultiplicationExpr(
    val left: SymbolicExpression,
    val right: SymbolicExpression
) : SymbolicExpression(), BinarySymbolicExpression {
    override fun left() = left
    override fun right() = right
    override fun type() = left.type() // TODO: Have to take common supertype

    override fun toString() = "(${left()})*(${right()})"
    override fun hashCode() = Objects.hash(MultiplicationExpr::class, left, right)
    override fun equals(other: Any?) =
        (other as? MultiplicationExpr).let { it?.left == left && it.right == right }

    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)
}

class LengthExpression(val of: SymbolicExpression) : SymbolicExpression() {
    init {
        assert(of.type() is ArrayType)
    }

    override fun type() = INT_TYPE
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)

    override fun toString() = "${of}.length"
    override fun hashCode() = Objects.hash(LengthExpression::class, of)
    override fun equals(other: Any?) = (other as? LengthExpression)?.of == of
}

class ArrayReference(val array: LocalVariable, val index: SymbolicExpression) : SymbolicExpression() {
    init {
        assert(index.type() == INT_TYPE)
    }

    override fun type() = (array.type as? ArrayType)!!.baseType
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)

    override fun toString() = "$array[$index]"
    override fun hashCode() = Objects.hash(ArrayReference::class, array, index)
    override fun equals(other: Any?) =
        (other as? ArrayReference).let { it?.array == array && it.index == index }
}

class MethodInvocationExpression(
    val obj: SymbolicExpression,
    val method: String,
    val declaringClass: ReferenceType,
    val type: Type,
    val args: List<SymbolicExpression>
) : SymbolicExpression() {
    override fun type() = type
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)

    override fun toString() = "${obj}.${method}(${args.joinToString(", ")})"
    override fun hashCode() = Objects.hash(MethodInvocationExpression::class, obj, method, declaringClass, type, args)
    override fun equals(other: Any?) =
        (other as? MethodInvocationExpression).let {
            it?.obj == obj && it.method == method && it.declaringClass == declaringClass && it.type == type && it.args == args
        }
}

object ExprConverter {
    private val logger = LoggerFactory.getLogger(ExprConverter::class.simpleName)

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
            is JimpleLocal, is IntConstant, is JAddExpr, is JMulExpr -> true
            else -> false
        }

    fun convert(value: soot.Value): SymbolicExpression =
        when (value) {
            is JimpleLocal -> LocalVariable(value.name, TypeConverter.convert(value.getType()))
            is IntConstant -> IntValue(value.value)
            is JAddExpr -> AdditionExpr(convert(value.op1), convert(value.op2))
            is JMulExpr -> MultiplicationExpr(convert(value.op1), convert(value.op2))
            is JLengthExpr -> LengthExpression(convert(value.op))
            is JArrayRef -> ArrayReference(convert(value.base) as LocalVariable, convert(value.index))
            is JVirtualInvokeExpr -> {
                logger.warn("Treating method ${value.methodRef} as pure")
                MethodInvocationExpression(
                    convert(value.base),
                    value.methodRef.name,
                    TypeConverter.convert(value.methodRef.declaringClass.type) as ReferenceType,
                    TypeConverter.convert(value.methodRef.returnType),
                    value.args.map { convert(it) }.toList()
                )
            }
            else -> TODO("Conversion of type ${value.javaClass} to SymbolicExpression not yet implemented.")
        }
}

class LocalVariableExpressionCollector() : SymbolicExpressionsVisitor<Set<LocalVariable>> {
    override fun visit(e: IntValue) = emptySet<LocalVariable>()

    override fun visit(e: LocalVariable) = setOf(e)

    override fun visit(e: FunctionApplication) = e.args.map { it.accept(this) }.flatten().toSet()

    override fun visit(e: StoreApplExpression) =
        listOf(e.applied.accept(LocalVariableStoreCollector()), e.target.accept(this)).flatten().toSet()

    override fun visit(e: ConditionalExpression) =
        listOf(
            e.condition.accept(LocalVariableConstraintCollector()),
            e.vThen.accept(this),
            e.vElse.accept(this)
        ).flatten().toSet()

    override fun visit(e: AdditionExpr) = listOf(e.left.accept(this), e.right.accept(this)).flatten().toSet()

    override fun visit(e: MultiplicationExpr) = listOf(e.left.accept(this), e.right.accept(this)).flatten().toSet()

    override fun visit(e: LengthExpression) = e.of.accept(this)

    override fun visit(e: ArrayReference) = listOf(listOf(e.array), e.index.accept(this)).flatten().toSet()

    override fun visit(e: MethodInvocationExpression) =
        listOf(e.obj.accept(this), e.args.map { it.accept(this) }.flatten()).flatten().toSet()

}

class SymbolReplaceExprVisitor(val replMap: Map<LocalVariable, SymbolicExpression>) :
    SymbolicExpressionsVisitor<SymbolicExpression> {
    override fun visit(e: IntValue): SymbolicExpression = e
    override fun visit(e: LocalVariable): SymbolicExpression = replMap[e] ?: e

    override fun visit(e: FunctionApplication): SymbolicExpression =
        FunctionApplication(e.f, e.args.map { it.accept(this) })

    override fun visit(e: ConditionalExpression): SymbolicExpression =
        ConditionalExpression.create(
            e.condition.accept(SymbolReplaceConstrVisitor(replMap)),
            e.vThen.accept(this),
            e.vElse.accept(this)
        )

    override fun visit(e: AdditionExpr): SymbolicExpression =
        AdditionExpr(e.left.accept(this), e.right.accept(this))

    override fun visit(e: MultiplicationExpr): SymbolicExpression =
        MultiplicationExpr(e.left.accept(this), e.right.accept(this))

    override fun visit(e: LengthExpression): SymbolicExpression =
        LengthExpression(e.of.accept(this))

    override fun visit(e: ArrayReference): SymbolicExpression =
        ArrayReference(e.array.accept(this) as LocalVariable, e.index.accept(this))

    override fun visit(e: MethodInvocationExpression): SymbolicExpression =
        MethodInvocationExpression(
            e.obj.accept(this),
            e.method,
            e.declaringClass,
            e.type,
            e.args.map { it.accept(this) }
        )

    override fun visit(e: StoreApplExpression): SymbolicExpression =
        throw UnsupportedOperationException("Symbol replacement below the scope of store application unsupported, normalize first")
}