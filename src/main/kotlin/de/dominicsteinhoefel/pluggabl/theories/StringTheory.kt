package de.dominicsteinhoefel.pluggabl.theories

import de.dominicsteinhoefel.pluggabl.expr.*
import soot.G
import soot.jimple.StringConstant
import java.util.*

object StringTheory : Theory {
    val STRING_TYPE = Type.create("java.lang.String", OBJECT_TYPE)

    class StringValue(val value: String) : Value() {
        override fun type() = STRING_TYPE
        override fun <T> accept(visitor: SymbolicExpressionsVisitor<T>) = visitor.visit(this)

        override fun toString() = value.toString()
        override fun hashCode() = Objects.hash(StringValue::class, value.hashCode())
        override fun equals(other: Any?) = (other as? StringValue)?.value == value
    }

    override fun getType() = STRING_TYPE
    override fun getSootType(): soot.Type? =
        G.v().soot_jimple_toolkits_pointer_representations_TypeConstants().STRINGCLASS

    override fun functions() = emptySet<FunctionSymbol>()

    override fun isResponsibleFor(jimpleExpr: soot.Value) =
        when (jimpleExpr) {
            is StringConstant -> true
            else -> false
        }

    override fun translate(jimpleExpr: soot.Value, subs: List<SymbolicExpression>) =
        when (jimpleExpr) {
            is StringConstant -> StringValue(jimpleExpr.value)
            else -> throw IllegalArgumentException()
        }
}