package de.dominicsteinhoefel.pluggabl.theories

import de.dominicsteinhoefel.pluggabl.expr.*

val LOCATION_SET_TYPE = Type("LocationSet")

val EMPTY = FunctionSymbol("empty", LOCATION_SET_TYPE)
val ALL_LOCS = FunctionSymbol("allLocs", LOCATION_SET_TYPE)
val SINGLETON = FunctionSymbol("singleton", LOCATION_SET_TYPE, OBJECT_TYPE, FIELD_TYPE)
val UNION = FunctionSymbol("union", LOCATION_SET_TYPE, LOCATION_SET_TYPE, LOCATION_SET_TYPE)
val INTERSECT = FunctionSymbol("intersect", LOCATION_SET_TYPE, LOCATION_SET_TYPE, LOCATION_SET_TYPE)
val SET_MINUS = FunctionSymbol("setMinus", LOCATION_SET_TYPE, LOCATION_SET_TYPE, LOCATION_SET_TYPE)

val LOCATION_SET_SYMBOLS = listOf(EMPTY, ALL_LOCS, SINGLETON, UNION, INTERSECT, SET_MINUS)