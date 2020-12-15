package de.dominicsteinhoefel.pluggabl.theories

import de.dominicsteinhoefel.pluggabl.expr.FunctionSymbol
import de.dominicsteinhoefel.pluggabl.expr.SymbolicExpression
import de.dominicsteinhoefel.pluggabl.expr.Type

interface Theory {

    fun getType(): Type

    fun getSootType(): soot.Type?

    fun functions(): Set<FunctionSymbol>

    fun isResponsibleFor(jimpleExpr: soot.Value): Boolean

    fun translate(jimpleExpr: soot.Value, subs: List<SymbolicExpression>): SymbolicExpression

}