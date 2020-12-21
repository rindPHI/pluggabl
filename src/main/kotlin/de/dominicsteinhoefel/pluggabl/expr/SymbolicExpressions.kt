package de.dominicsteinhoefel.pluggabl.expr

import de.dominicsteinhoefel.pluggabl.theories.HeapTheory
import java.util.*

interface SymbolicExpression {
    fun type(): Type
    fun <T> accept(visitor: SymbolicExpressionsVisitor<T>): T
}

abstract class Value : SymbolicExpression

interface BinarySymbolicExpression {
    fun left(): SymbolicExpression
    fun right(): SymbolicExpression
}

interface Location : SymbolicExpression {
    fun toSelectExpr(): SymbolicExpression
    fun toSelectAllExpr(): SymbolicExpression = toSelectExpr()
}

interface HeapExpression : Location

class LocalVariable(
    val name: String,
    val type: Type
) : SymbolicExpression, Location {
    override fun type() = type
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)
    override fun toSelectExpr() = this

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
    override fun hashCode() = Objects.hash(FunctionSymbol::class, name, type, paramTypes, unique)
    override fun equals(other: Any?) =
        (other as? FunctionSymbol).let { it?.name == name && it.type == type && it.paramTypes == paramTypes && it.unique == unique }
}

class Field(val containerType: ReferenceType, val fieldType: Type, val fieldName: String) :
    FunctionSymbol("<$containerType: $fieldType $fieldName>", HeapTheory.FIELD_TYPE, emptyList(), true)

class FieldRef(
    val obj: SymbolicExpression,
    val field: Field
) : SymbolicExpression, HeapExpression {
    override fun type() = field.fieldType
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)
    override fun toSelectExpr() =
        FunctionApplication(HeapTheory.Select.create(type()), HeapTheory.HEAP_VAR, obj, FunctionApplication(field))

    override fun toString() = "$obj.${field.fieldName}"
    override fun hashCode() = Objects.hash(FieldRef::class, field, obj)
    override fun equals(other: Any?) =
        (other as? FieldRef).let { it?.field == field && it.obj == obj }
}

class ArrayRef(
    val base: SymbolicExpression,
    val index: SymbolicExpression
) : SymbolicExpression, HeapExpression {
    override fun type() = (base.type() as ArrayType).baseType
    override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)

    override fun toSelectExpr() =
        FunctionApplication(
            HeapTheory.Select.create(type()),
            HeapTheory.HEAP_VAR,
            base,
            FunctionApplication(HeapTheory.ARRAY_FIELD, index)
        )

    override fun toSelectAllExpr() =
        FunctionApplication(
            HeapTheory.SelectAll.create(type()),
            HeapTheory.HEAP_VAR,
            base
        )

    override fun toString() = "$base[$index]"
    override fun hashCode() = Objects.hash(ArrayRef::class, base, index)
    override fun equals(other: Any?) =
        (other as? ArrayRef).let { it?.base == base && it.index == index }
}

class FunctionApplication(
    val f: FunctionSymbol,
    val args: List<SymbolicExpression>
) : SymbolicExpression {
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
    SymbolicExpression {
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
) : SymbolicExpression {
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