package de.dominicsteinhoefel.pluggabl.expr

import de.dominicsteinhoefel.pluggabl.analysis.SymbolsManager
import de.dominicsteinhoefel.pluggabl.simplification.SymbolicConstraintSimplifier
import de.dominicsteinhoefel.pluggabl.util.union
import soot.Value
import soot.jimple.internal.JEqExpr
import soot.jimple.internal.JGeExpr
import soot.jimple.internal.JGtExpr
import soot.jimple.internal.JNeExpr
import java.util.*
import kotlin.collections.LinkedHashSet

sealed class SymbolicConstraint {
    abstract fun <T> accept(v: SymbolicConstraintVisitor<T>): T
}

class SymbolicConstraintSet : Set<SymbolicConstraint> {
    private val backSet: LinkedHashSet<SymbolicConstraint>

    constructor() {
        this.backSet = LinkedHashSet()
    }

    private constructor(backSet: Collection<SymbolicConstraint>) {
        if (backSet.contains(False) && backSet.size > 1) {
            this.backSet = linkedSetOf(False)
        } else {
            this.backSet = LinkedHashSet(backSet)
        }
    }

    fun simplify() = SymbolicConstraintSet(
        SymbolicConstraintSimplifier.compress(
            SymbolicConstraintSimplifier.substituteFacts(
                backSet.asSequence()
                    .map { SymbolicConstraintSimplifier.simplify(it) }
                    .map { SymbolicConstraintSimplifier.toCNFClauses(it) }.flatten().toSet()
            )
        )
    )

    fun add(element: SymbolicConstraint) =
        if (element is True) this
        else SymbolicConstraintSet(listOf(backSet, listOf(element)).flatten()).remove(True)

    fun addAll(elements: Collection<SymbolicConstraint>) =
        SymbolicConstraintSet(listOf(backSet, elements).flatten()).remove(True)

    fun remove(element: SymbolicConstraint) =
        SymbolicConstraintSet(backSet.filter { it != element })

    override val size: Int get() = backSet.size
    override fun contains(element: SymbolicConstraint) = backSet.contains(element)
    override fun containsAll(elements: Collection<SymbolicConstraint>) = backSet.containsAll(elements)
    override fun isEmpty() = backSet.isEmpty()
    override fun iterator() = backSet.iterator()
    override fun equals(other: Any?) = backSet == other
    override fun hashCode() = Objects.hash(SymbolicConstraintSet::class, backSet)

    companion object {
        fun from(constraints: Collection<SymbolicConstraint>) =
            SymbolicConstraintSet().addAll(constraints)

        fun from(vararg constraints: SymbolicConstraint) =
            SymbolicConstraintSet().addAll(constraints.asList())
    }
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
    override fun equals(other: Any?) = other === True
    override fun hashCode() = True::class.hashCode()
}

object False : SymbolicConstraint() {
    override fun <T> accept(v: SymbolicConstraintVisitor<T>): T = v.visit(this)

    override fun toString() = "False"
    override fun equals(other: Any?) = other === False
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
                is True -> False
                is False -> True
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

        fun create(constraints: Collection<SymbolicConstraint>) =
            constraints.fold(False as SymbolicConstraint, { acc, elem -> Or.create(acc, elem) })
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

        fun create(constraints: Collection<SymbolicConstraint>) =
            constraints.fold(True as SymbolicConstraint, { acc, elem -> And.create(acc, elem) })

        fun create(vararg constraints: SymbolicConstraint) = create(constraints.toList())
    }
}