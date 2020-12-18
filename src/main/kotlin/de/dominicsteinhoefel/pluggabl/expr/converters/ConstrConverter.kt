package de.dominicsteinhoefel.pluggabl.expr.converters

import de.dominicsteinhoefel.pluggabl.expr.*
import soot.Value
import soot.jimple.internal.JEqExpr
import soot.jimple.internal.JGeExpr
import soot.jimple.internal.JGtExpr
import soot.jimple.internal.JNeExpr

class ConstrConverter(private val exprConverter: ExprConverter) {
    fun convert(value: Value): SymbolicConstraint =
        when (value) {
            is JEqExpr ->
                EqualityConstr.create(
                    exprConverter.convert(value.op1),
                    exprConverter.convert(value.op2)
                )
            is JNeExpr -> NegatedConstr.create(
                EqualityConstr.create(
                    exprConverter.convert(value.op1),
                    exprConverter.convert(value.op2)
                )
            )
            is JGeExpr -> GreaterEqualConstr(
                exprConverter.convert(value.op1), exprConverter.convert(value.op2)
            )
            is JGtExpr -> GreaterConstr(
                exprConverter.convert(value.op1), exprConverter.convert(value.op2)
            )
            else -> TODO("Conversion of type ${value.javaClass} to SymbolicConstraint not yet implemented.")
        }
}