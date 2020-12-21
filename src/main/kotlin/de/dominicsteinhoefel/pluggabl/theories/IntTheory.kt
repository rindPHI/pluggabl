package de.dominicsteinhoefel.pluggabl.theories

import de.dominicsteinhoefel.pluggabl.expr.*
import soot.G
import soot.jimple.IntConstant
import soot.jimple.internal.JAddExpr
import soot.jimple.internal.JDivExpr
import soot.jimple.internal.JMulExpr
import soot.jimple.internal.JSubExpr
import java.util.*

object IntTheory : Theory {
    val INT_TYPE = Type.create("int", Type.ANY_TYPE)

    val ADD_INT = FunctionSymbol("plusInt", INT_TYPE, INT_TYPE, INT_TYPE, unique = true)
    val SUB_INT = FunctionSymbol("subInt", INT_TYPE, INT_TYPE, INT_TYPE, unique = true)
    val MUL_INT = FunctionSymbol("mulInt", INT_TYPE, INT_TYPE, INT_TYPE, unique = true)
    val DIV_INT = FunctionSymbol("divInt", INT_TYPE, INT_TYPE, INT_TYPE, unique = true)

    class IntValue(val value: Int) : Value() {
        override fun type() = INT_TYPE
        override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)

        override fun toString() = value.toString()
        override fun hashCode() = Objects.hash(IntValue::class, value.hashCode())
        override fun equals(other: Any?) = (other as? IntValue)?.value == value
    }

    override fun getType() = INT_TYPE
    override fun getSootType(): soot.Type? = G.v().soot_IntType()

    override fun functions() = setOf(ADD_INT, SUB_INT, MUL_INT, DIV_INT)

    override fun isResponsibleFor(jimpleExpr: soot.Value) =
        when (jimpleExpr) {
            is IntConstant,
            is JAddExpr, is JSubExpr,
            is JMulExpr, is JDivExpr -> true
            else -> false
        }

    override fun translate(jimpleExpr: soot.Value, subs: List<SymbolicExpression>) =
        when (jimpleExpr) {
            is IntConstant -> IntValue(jimpleExpr.value)
            is JAddExpr -> FunctionApplication(ADD_INT, subs)
            is JSubExpr -> FunctionApplication(SUB_INT, subs)
            is JMulExpr -> FunctionApplication(MUL_INT, subs)
            is JDivExpr -> FunctionApplication(DIV_INT, subs)
            else -> throw IllegalArgumentException()
        }

    // Convenience Functions

    fun plus(expr1: SymbolicExpression, expr2: SymbolicExpression) =
        FunctionApplication(ADD_INT, expr1, expr2)

    fun minus(expr1: SymbolicExpression, expr2: SymbolicExpression) =
        FunctionApplication(SUB_INT, expr1, expr2)

    fun mult(expr1: SymbolicExpression, expr2: SymbolicExpression) =
        FunctionApplication(MUL_INT, expr1, expr2)
}