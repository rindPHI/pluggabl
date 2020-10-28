package de.dominicsteinhoefel.symbex.expr

import soot.jimple.internal.*
import java.util.*
import kotlin.collections.LinkedHashSet

sealed class SymbolicConstraint {
    abstract fun <T> accept(v: SymbolicConstraintVisitor<T>): T
}

class SymbolicConstraintSet : LinkedHashSet<SymbolicConstraint>() {
    override fun add(element: SymbolicConstraint) =
        if (element is True) false
        else super.add(element) || this.remove(True)

    override fun addAll(elements: Collection<SymbolicConstraint>) =
        elements.map { add(it) }.fold(false, { acc, elem -> acc || elem })

    companion object {
        fun from(vararg constraints: SymbolicConstraint) =
            SymbolicConstraintSet().let { set -> constraints.forEach { set.add(it) }; set }
    }
}

interface SymbolicConstraintVisitor<T> {
    fun visit(c: StoreApplConstraint): T
    fun visit(c: True): T
    fun visit(c: False): T
    fun visit(c: GreaterConstr): T
    fun visit(c: EqualityConstr): T
    fun visit(c: NegatedConstr): T
    fun visit(c: Or): T
    fun visit(c: And): T
    fun visit(c: GreaterEqualConstr): T
}

class StoreApplConstraint private constructor(val applied: SymbolicStore, val target: SymbolicConstraint) :
    SymbolicConstraint() {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)

    override fun toString() = "{${applied}}(${target})"

    companion object {
        fun create(applied: SymbolicStore, target: SymbolicConstraint) =
            if (applied is EmptyStore) target else
                StoreApplConstraint(applied, target)
    }
}

interface UnarySymbolicConstraint {
    fun inner(): SymbolicConstraint
}

interface BinarySymbolicConstraint {
    fun left(): SymbolicExpression
    fun right(): SymbolicExpression
}

object True : SymbolicConstraint() {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)

    override fun toString() = "True"
    override fun equals(other: Any?) = other == True
    override fun hashCode() = True::class.hashCode()
}

object False : SymbolicConstraint() {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)

    override fun toString() = "False"
    override fun equals(other: Any?) = other == False
    override fun hashCode() = False::class.hashCode()
}

class GreaterConstr(
    val left: SymbolicExpression,
    val right: SymbolicExpression
) : SymbolicConstraint(), BinarySymbolicConstraint {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)
    override fun left() = left
    override fun right() = right

    override fun toString() = "(${left()})>(${right()})"
    override fun equals(other: Any?) = (other as? GreaterConstr).let { it?.left == left && it.right == right }
    override fun hashCode() = Objects.hash(left, right, GreaterConstr::class)
}

class GreaterEqualConstr(
    val left: SymbolicExpression,
    val right: SymbolicExpression
) : SymbolicConstraint(), BinarySymbolicConstraint {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)
    override fun left() = left
    override fun right() = right

    override fun toString() = "(${left()})>=(${right()})"
    override fun equals(other: Any?) = (other as? GreaterEqualConstr).let { it?.left == left && it.right == right }
    override fun hashCode() = Objects.hash(left, right, GreaterEqualConstr::class)
}

class EqualityConstr private constructor(
    val left: SymbolicExpression,
    val right: SymbolicExpression
) : SymbolicConstraint(), BinarySymbolicConstraint {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)
    override fun left() = left
    override fun right() = right

    override fun toString() = "(${left()})==(${right()})"
    override fun equals(other: Any?) = (other as? EqualityConstr).let { it?.left == left && it.right == right }
    override fun hashCode() = Objects.hash(left, right, EqualityConstr::class)

    companion object {
        fun create(left: SymbolicExpression, right: SymbolicExpression) =
            if (left == right) True else EqualityConstr(left, right)
    }
}

class NegatedConstr private constructor(
    val constr: SymbolicConstraint
) : SymbolicConstraint(), UnarySymbolicConstraint {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)
    override fun inner() = constr

    override fun toString() = "!(${constr})"
    override fun equals(other: Any?) = (other as? NegatedConstr)?.constr == constr
    override fun hashCode() = Objects.hash(constr, NegatedConstr::class)

    companion object {
        fun create(constr: SymbolicConstraint) =
            when (constr) {
                is NegatedConstr -> constr.constr
                else -> NegatedConstr(constr)
            }
    }
}

interface ConstraintJunctor {
    fun left(): SymbolicConstraint
    fun right(): SymbolicConstraint
}

class Or private constructor(val left: SymbolicConstraint, val right: SymbolicConstraint) : SymbolicConstraint(),
    ConstraintJunctor {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)
    override fun left() = left
    override fun right() = right

    override fun toString() = "(${left})||(${right})"
    override fun equals(other: Any?) = (other as? Or).let { it?.left == left && it.right == right }
    override fun hashCode() = Objects.hash(left, right, Or::class)

    companion object {
        fun create(left: SymbolicConstraint, right: SymbolicConstraint) =
            if (left is True || right is True) True else
                if (left is False) right else
                    if (right is False) left else
                        if (left == right) left else
                            if (left is NegatedConstr && left.constr == right) True else
                                if (right is NegatedConstr && right.constr == left) True else
                                    Or(left, right)
    }
}

class And private constructor(val left: SymbolicConstraint, val right: SymbolicConstraint) : SymbolicConstraint(),
    ConstraintJunctor {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)
    override fun left() = left
    override fun right() = right

    override fun toString() = "(${left})&&(${right})"
    override fun equals(other: Any?) = (other as? And).let { it?.left == left && it.right == right }
    override fun hashCode() = Objects.hash(left, right, And::class)

    companion object {
        fun create(left: SymbolicConstraint, right: SymbolicConstraint) =
            if (left is False || right is False) False else
                if (left is True) right else
                    if (right is True) left else
                        if (left == right) left else
                            if (left is NegatedConstr && left.constr == right) False else
                                if (right is NegatedConstr && right.constr == left) False else
                                    And(left, right)
    }
}

class SymbolReplaceConstrVisitor(val replMap: Map<Symbol, SymbolicExpression>) :
    SymbolicConstraintVisitor<SymbolicConstraint> {
    override fun visit(c: True): SymbolicConstraint = c
    override fun visit(c: False): SymbolicConstraint = c

    override fun visit(c: GreaterConstr) =
        SymbolReplaceExprVisitor(replMap).let {
            GreaterConstr(c.left.accept(it), c.right.accept(it))
        }

    override fun visit(c: GreaterEqualConstr) =
        SymbolReplaceExprVisitor(replMap).let {
            GreaterEqualConstr(c.left.accept(it), c.right.accept(it))
        }

    override fun visit(c: EqualityConstr) =
        SymbolReplaceExprVisitor(replMap).let {
            EqualityConstr.create(c.left.accept(it), c.right.accept(it))
        }

    override fun visit(c: NegatedConstr): SymbolicConstraint =
        NegatedConstr.create(c.constr.accept(this))

    override fun visit(c: Or): SymbolicConstraint =
        Or.create(c.left.accept(this), c.right.accept(this))

    override fun visit(c: And): SymbolicConstraint =
        And.create(c.left.accept(this), c.right.accept(this))

    override fun visit(c: StoreApplConstraint): SymbolicConstraint =
        throw UnsupportedOperationException("Symbol replacement below the scope of store application unsupported, normalize first")
}

object ConstrConverter {
    fun convert(value: soot.Value): SymbolicConstraint =
        when (value) {
            is JEqExpr ->
                EqualityConstr.create(
                    ExprConverter.convert(value.op1),
                    ExprConverter.convert(value.op2)
                )
            is JNeExpr -> NegatedConstr.create(
                EqualityConstr.create(
                    ExprConverter.convert(value.op1),
                    ExprConverter.convert(value.op2)
                )
            )
            is JGeExpr -> GreaterEqualConstr(
                ExprConverter.convert(value.op1), ExprConverter.convert(value.op2)
            )
            is JGtExpr -> GreaterConstr(
                ExprConverter.convert(value.op1), ExprConverter.convert(value.op2)
            )
            else -> TODO("Conversion of type ${value.javaClass} to SymbolicConstraint not yet implemented.")
        }
}