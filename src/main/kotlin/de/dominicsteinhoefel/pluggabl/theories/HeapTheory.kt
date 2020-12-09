package de.dominicsteinhoefel.pluggabl.theories

import de.dominicsteinhoefel.pluggabl.expr.*
import soot.jimple.internal.JInstanceFieldRef

val HEAP_TYPE = Type("Heap")
val FIELD_TYPE = Type("Field")

val HEAP_VAR = LocalVariable("heap", HEAP_TYPE)
val STORE = FunctionSymbol("store", HEAP_TYPE, HEAP_TYPE, OBJECT_TYPE, FIELD_TYPE, ANY_TYPE)
val CREATE = FunctionSymbol("create", HEAP_TYPE, HEAP_TYPE, OBJECT_TYPE)
val ANON = FunctionSymbol("anon", HEAP_TYPE, LOCATION_SET_TYPE, HEAP_TYPE)

val ARRAY_FIELD = FunctionSymbol("arr", FIELD_TYPE, INT_TYPE)
val ARRAY_LENGTH = FunctionSymbol("length", INT_TYPE, OBJECT_TYPE)

class Select(type: Type) : FunctionSymbol("select", type, HEAP_TYPE, OBJECT_TYPE, FIELD_TYPE)

fun getFieldSymbol(fieldRef: JInstanceFieldRef) =
    FunctionApplication(
        FunctionSymbol(
            "<${fieldRef.fieldRef.declaringClass()}: ${fieldRef.type} ${fieldRef.fieldRef.name()}>",
            FIELD_TYPE
        )
    )
