package de.dominicsteinhoefel.symbex.expr

import soot.jimple.IntConstant
import soot.jimple.internal.JAddExpr
import soot.jimple.internal.JEqExpr
import soot.jimple.internal.JNeExpr
import soot.jimple.internal.JimpleLocal

interface SymbolicConstraintVisitor<T> {
    fun visit(c: StoreApplConstraint): T
    fun visit(c: True): T
    fun visit(c: False): T
    fun visit(c: GreaterConstr): T
    fun visit(c: EqualityConstr): T
    fun visit(c: NegatedConstr): T
    fun visit(c: Or): T
    fun visit(c: And): T
}

sealed class SymbolicConstraint {
    abstract fun <T> accept(v: SymbolicConstraintVisitor<T>): T
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
}

object False : SymbolicConstraint() {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)
    override fun toString() = "False"
}

data class GreaterConstr(
    val left: SymbolicExpression,
    val right: SymbolicExpression
) : SymbolicConstraint(), BinarySymbolicConstraint {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)
    override fun left() = left
    override fun right() = right
    override fun toString() = "(${left()})>(${right()})"
}

data class EqualityConstr(
    val left: SymbolicExpression,
    val right: SymbolicExpression
) : SymbolicConstraint(), BinarySymbolicConstraint {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)
    override fun left() = left
    override fun right() = right
    override fun toString() = "(${left()})==(${right()})"
}

data class NegatedConstr private constructor(
    val constr: SymbolicConstraint
) : SymbolicConstraint(), UnarySymbolicConstraint {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)
    override fun inner() = constr
    override fun toString() = "!(${constr})"

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

data class Or private constructor(val left: SymbolicConstraint, val right: SymbolicConstraint) : SymbolicConstraint(),
    ConstraintJunctor {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)
    override fun left() = left
    override fun right() = right
    override fun toString() = "(${left})||(${right})"

    companion object {
        fun create(left: SymbolicConstraint, right: SymbolicConstraint) =
            if (left is True) True else
                if (right is True) True else
                    if (left is False) right else
                        if (right is False) left else
                            Or(left, right)
    }
}

data class And private constructor(val left: SymbolicConstraint, val right: SymbolicConstraint) : SymbolicConstraint(),
    ConstraintJunctor {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)
    override fun left() = left
    override fun right() = right
    override fun toString() = "(${left})&&(${right})"

    companion object {
        fun create(left: SymbolicConstraint, right: SymbolicConstraint) =
            if (left is False) False else
                if (right is False) False else
                    if (left is True) right else
                        if (right is True) left else
                            And(left, right)
    }
}

class SymbolReplaceConstrVisitor(val replMap: Map<Symbol, SymbolicExpression>) :
    SymbolicConstraintVisitor<SymbolicConstraint> {
    override fun visit(c: True): SymbolicConstraint = c
    override fun visit(c: False): SymbolicConstraint = c

    override fun visit(c: GreaterConstr): SymbolicConstraint {
        val exprVisitor = SymbolReplaceExprVisitor(replMap)
        return GreaterConstr(c.left.accept(exprVisitor), c.right.accept(exprVisitor))
    }

    override fun visit(c: EqualityConstr): SymbolicConstraint {
        val exprVisitor = SymbolReplaceExprVisitor(replMap)
        return EqualityConstr(c.left.accept(exprVisitor), c.right.accept(exprVisitor))
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
                EqualityConstr(
                    ExprConverter.convert(value.op1),
                    ExprConverter.convert(value.op2)
                )
            is JNeExpr -> NegatedConstr.create(
                EqualityConstr(
                    ExprConverter.convert(value.op1),
                    ExprConverter.convert(value.op2)
                )
            )
            else -> TODO("Conversion of type ${value.getType()} to SymbolicConstraint not yet implemented.")
        }
}