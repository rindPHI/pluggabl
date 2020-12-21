package de.dominicsteinhoefel.pluggabl.theories

import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory.FIELD_TYPE
import soot.Value

object LocationSetTheory : Theory {
    val LOCATION_SET_TYPE = Type.create("LocationSet", Type.ANY_TYPE)

    val EMPTY = FunctionSymbol("empty", LOCATION_SET_TYPE)
    val ALL_LOCS = FunctionSymbol("allLocs", LOCATION_SET_TYPE)
    val SINGLETON = FunctionSymbol("singleton", LOCATION_SET_TYPE, OBJECT_TYPE, FIELD_TYPE)
    val UNION = FunctionSymbol("union", LOCATION_SET_TYPE, LOCATION_SET_TYPE, LOCATION_SET_TYPE)
    val INTERSECT = FunctionSymbol("intersect", LOCATION_SET_TYPE, LOCATION_SET_TYPE, LOCATION_SET_TYPE)
    val SET_MINUS = FunctionSymbol("setMinus", LOCATION_SET_TYPE, LOCATION_SET_TYPE, LOCATION_SET_TYPE)

    override fun getType() = LOCATION_SET_TYPE
    override fun getSootType(): soot.Type? = null

    override fun functions() = setOf(EMPTY, ALL_LOCS, SINGLETON, UNION, INTERSECT, SET_MINUS)

    override fun isResponsibleFor(jimpleExpr: Value) = false

    override fun translate(jimpleExpr: Value, subs: List<SymbolicExpression>): SymbolicExpression {
        throw UnsupportedOperationException("Location set theory does not correspond to bytecode expressions.")
    }

}